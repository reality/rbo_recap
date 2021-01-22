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

// Load the list of terms that are imported through the ODK imports system
def importedTerms = []
new File('../RBO/src/ontology/imports/').eachFile{ f ->
  if(f.getName() =~ /txt$/) {
    f.eachLine { l ->
      def z = l =~ /([A-Z]+)(_|:)([0-9]+)/
      if(z.size() > 0) { // .matches() doesn't work if there's other line content. hmm.
        importedTerms <<  z[0][0].replace(':','_')
      }
    }
  }
}

// Load RBO
def ontologyPath = '../RBO/rbo-full.owl'
def manager = OWLManager.createOWLOntologyManager()
def fac = manager.getOWLDataFactory()
def rbo = manager.loadOntologyFromOntologyDocument(new File(ontologyPath))

def orphanedImports = []
def nonOrphanCount = 0
rbo.getClassesInSignature(false).each { cl ->
  def iri = cl.getIRI().tokenize('/').last()
  if(iri.indexOf('RBO') == -1 && !importedTerms.contains(iri)) {
    orphanedImports << iri
  } else {
    nonOrphanCount++
  }
}

println "Orphaned imports: ${orphanedImports.size()}"
orphanedImports.each {
println it
}
