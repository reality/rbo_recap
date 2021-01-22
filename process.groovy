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

def importedTerms = [:]
new File('../RBO/src/ontology/imports/').eachFile{ f ->
  if(f.getName() =~ /txt$/) {
    def name = f.getName().tokenize('_')[0].toUpperCase()
    importedTerms[name] = []
    f.eachLine { l ->
      def z = l =~ /([A-Z]+)(_|:)([0-9]+)/
      if(z.size() > 0) { // .matches() doesn't work if there's other line content. hmm.
        importedTerms[name] <<  z[0][0].replace(':','_')
      }
    }
  }
}

// update old RBO iri to new one
def convertIRI = { 'http://purl.obolibrary.org/obo/RBO_' + it.toString().split(':').last() }
// bad, need to abstract. quicker to copy paste
def extractLabels = { cl, o ->
  def labels = []

  EntitySearcher.getAnnotations(cl, o).each { anno ->
    def property = anno.getProperty()
      OWLAnnotationValue val = anno.getValue()
      if(val instanceof OWLLiteral) {
        def literal = val.getLiteral()
        if(property.isLabel() || property =~ /Synonym/) {
          labels << literal.toLowerCase()
        } 
      }
  }

  labels
}
def extractDescription = { cl, o ->
  def label = ''

  EntitySearcher.getAnnotations(cl, o).each { anno ->
    def property = anno.getProperty()
    if(property.toString() == '<http://purl.obolibrary.org/obo/IAO_0000115>' || property.toString() == 'rdfs:description') {
      OWLAnnotationValue val = anno.getValue()
      if(val instanceof OWLLiteral) {
        def literal = val.getLiteral()
        label = literal
      }
    }
  }

  label
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

def report = []

def oldRBOClassMap = [:]
def addCounter = 100

def toAdd = [:]

oldRBO.getClassesInSignature(false).each { cl ->
  def iri = cl.getIRI()
  if(iri =~ /RBO/) {
    def labels = extractLabels(cl, oldRBO)
    oldRBOClassMap[iri] = [
      labels: labels,
      desc: extractDescription(cl, oldRBO),
      hasSuperclassInNewRBO: oldRBO.getSubClassAxiomsForSubClass(cl).any { scAxiom ->
        newRBOClassMap.containsKey(scAxiom.getSuperClass().getIRI().toString())
      },
      parent: oldRBO.getSubClassAxiomsForSubClass(cl).collect { scAxiom ->
        scAxiom.getSuperClass().getIRI().toString()
      }[0],
      matchingLabelInNewRBO: newRBOClassMap.any { nIri, nLabels -> nLabels.any { l -> labels.contains(l)} }, // bad time complexity
      matchingIRIInNewRBO: newRBOClassMap.containsKey(convertIRI(iri))
    ]
    if(!oldRBOClassMap[iri].matchingLabelInNewRBO) {
      toAdd[iri] = "obo:RBO_00000${addCounter}\t${labels[0]}\t\t\t\t${oldRBOClassMap[iri].desc}"
      report << 'added missing class obo:RBO_00000 ' + addCounter
      addCounter++
    }
  } else {
    // here we process the imports 
    def oboid = iri.tokenize('/').last()
    def (oid, id) = oboid.split('_')
    if(importedTerms.containsKey(oid) && !importedTerms[oid].contains(oboid)) {
      //new File('../RBO/src/ontology/imports/'+oid.toLowerCase()+'_terms.txt').text += '\nobo:' + oboid
      importedTerms[oid] << oboid
      report << 'Added import for ' + oboid
    } else if(!importedTerms.containsKey(oid)) {
      //new File('../RBO/src/ontology/imports/'+oid.toLowerCase()+'_terms.txt').text = 'obo:' + oboid
      importedTerms[oid] = [oboid]
      report << 'Added import for ' + oboid
    }
  }
}

toAdd.each { k, v ->
  if(oldRBOClassMap[k].parent && toAdd[oldRBOClassMap[k].parent]) {
   v+='\t\t\t\t\t\t\t\t\t\t'+toAdd[oldRBOClassMap[k].parent].split('\t')[0]
  }
  new File('../RBO/src/templates/RBO_classes.tsv').text += '\n' + v
}

new File('report.txt').text = report.join('\n')

println "Classes in old RBO: ${oldRBOClassMap.size()}"

def matchingIRI = oldRBOClassMap.findAll { it.getValue().matchingIRIInNewRBO }
println "Classes in old RBO with matching (transformed) IRI in new RBO: ${matchingIRI.size()}"
 
def matchingLabel = oldRBOClassMap.findAll { it.getValue().matchingLabelInNewRBO }
println "Classes in old RBO which have a label or synonym that exactly matches a class in new RBO: ${matchingLabel.size()}"

def matchingSuperclass = oldRBOClassMap.findAll { it.getValue().hasSuperclassInNewRBO }
println "Classes in old RBO whose direct superclass still exists in new RBO: ${matchingSuperclass.size()}"

def noMatches = oldRBOClassMap.findAll { i, v -> !v.hasSuperclassInNewRBO && !v.matchingIRIInNewRBO && !v.matchingLabelInNewRBO }
println "Classes in old RBO which have no matching IRI, label, or superclass in new RBO: ${noMatches.size()}"
