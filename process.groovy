@Grapes([
    @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.3'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='5.1.14'),
    @GrabConfig(systemClassLoader=true)
])

import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.owlapi.owllink.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.manchestersyntax.renderer.*
import org.semanticweb.owlapi.reasoner.structural.*

// update old RBO iri to new one
def convertIRI = { 'http://purl.obolibrary.org/obo/RBO_' + it.toString().split(':').last() }
def extractLabels = { cl, o ->
  def labels = []

  EntitySearcher.getAnnotations(cl, o).each { anno ->
    def property = anno.getProperty()
    OWLAnnotationValue val = anno.getValue()
    if(val instanceof OWLLiteral) {
      def literal = val.getLiteral()
      if(property.isLabel()) {
        labels = [literal] + labels
      } else {
        labels << literal
      }
    }
  }

  labels
}

def oldOntologyPath = './radiobiology-ontology.owl'
def newOntologyPath = './rbo-10-27-20-ontologies-owl-REVISION-344/RBO.owl'

def manager = OWLManager.createOWLOntologyManager()
def fac = manager.getOWLDataFactory()

def oldRBO = manager.loadOntologyFromOntologyDocument(new File(oldOntologyPath))
def newRBO = manager.loadOntologyFromOntologyDocument(new File(newOntologyPath))

// First we will build a dictionary of classes/iris in the new RBO
def newRBOClassMap = [:]
newRBO.getClassesInSignature(true).each { cl ->
  newRBOClassMap[cl.getIRI().toString()] = extractLabels(cl, newRBO)
}

def oldRBOClassMap = [:]
oldRBO.getClassesInSignature(false).each { cl ->
  def iri = cl.getIRI()
  if(iri =~ /RBO/) {
    def labels = extractLabels(cl, oldRBO)
    oldRBOClassMap[iri] = [
      labels: labels,
      hasSuperclassInNewRBO: oldRBO.getSubClassAxiomsForSubClass(cl).any { scAxiom ->
        newRBOClassMap.containsKey(scAxiom.getSuperClass().getIRI().toString())
      },
      matchingLabelInNewRBO: newRBOClassMap.any { nIri, nLabels -> nLabels.any { l -> labels.contains(l)} }, // bad time complexity
      matchingIRIInNewRBO: newRBOClassMap.containsKey(convertIRI(iri))
    ]
  }
}

println "Classes in old RBO: ${oldRBOClassMap.size()}"

def matchingIRI = oldRBOClassMap.findAll { it.getValue().matchingIRIInNewRBO }
println "Classes in old RBO with matching (transformed) IRI in new RBO: ${matchingIRI.size()}"
 
def matchingLabel = oldRBOClassMap.findAll { it.getValue().matchingLabelInNewRBO }
println "Classes in old RBO which have a label or synonym that exactly matches a class in new RBO: ${matchingLabel.size()}"

def matchingSuperclass = oldRBOClassMap.findAll { it.getValue().hasSuperclassInNewRBO }
println "Classes in old RBO whose direct superclass still exists in new RBO: ${matchingSuperclass.size()}"

def noMatches = oldRBOClassMap.findAll { i, v -> v.hasSuperclassInNewRBO || v.matchingIRIInNewRBO || v.matchingLabelInNewRBO }
println "Classes in old RBO which have no matching IRI, label, or superclass in new RBO: ${noMatches.size()}"
