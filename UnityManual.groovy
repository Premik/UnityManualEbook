#!/bin/groovy
@Grapes([
	@Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
	@Grab(group='ch.qos.logback', module='logback-classic', version='1.1.7')
])

import org.cyberneko.html.parsers.SAXParser
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import java.nio.file.Path
import java.nio.file.Paths


@Slf4j
class HtmlProcessor {
	
	def maxSubChaptersInOneFile = 6
	def removeLocalUrlLinks = true
	
	
	def toc = [:]
	private String outputDir
	private String rootDir

	private chaptersInFilePart
	private rootChapterCounter
	private filePartCounter
	private Map<String, Object> currentChapter
	private Writer writter
	private String rootChapterName
	
	public HtmlProcessor(String rootPath, String subFolderName) {
		rootDir = "$rootPath/$subFolderName"
		outputDir = "$rootPath/$subFolderName-flattened"
	}

	public String getTitle() {
		return currentChapter?.title
	}

	public String getLink() {
		return currentChapter?.link
	}

	public List<Map<String, Object>> getChildren() {
		def ch = currentChapter?.children
		if (!ch || ch == 'null') return Collections.emptyList()
		return ch
	}

	
	private void openNewWritter() {
		if (writter) closeWritter()		
		String ch = rootChapterCounter.toString().padLeft(2, '0')
		String part = ""
		part = "part${('A'..'Z')[filePartCounter]}-"
		File f = new File("$outputDir/$ch-$rootChapterName-$part${link}.html")
		chaptersInFilePart = 0
		writter = f.newWriter('UTF-8')
		
		writter.write(
			$/<!DOCTYPE html><html lang="en">
				<head>
				<meta charset=utf-8><title>$rootChapterName</title>
			</head><body>
		/$)
		
		//writter.write('<?xml version="1.0"?>')
		//writter.write('<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en"><head>')
		//writter.write('<meta charset="UTF-8">')
		//writter.write("<title>$rootChapterName</title></head><body>\n")
		
		
	}

	private void closeWritter() {
		assert writter
		writter.write('''</body></html>''')
		writter.close()
		writter = null
	}


	private void loadToc() {
		String tocFile = "$rootDir/docdata/toc.json"

		log.trace("Loading toc $tocFile")
		def jsonSlurper = new JsonSlurper()
		toc = jsonSlurper.parse(new File(tocFile))
		toc.children.each { log.debug("Chapter:  ${it.title.trim()} [${it.link} ${it.children?.size()}] ")}
		log.debug('-'*40)
		new File(outputDir).mkdir()
		chaptersInFilePart = -1
		rootChapterCounter=0
	}

	private String GPathToString(GPathResult node) {
		
		def builder = groovy.xml.StreamingMarkupBuilder.newInstance();
		Writable w = builder.bindNode(node)
		String ret = w.toString() +"\n"
		//HACK Remove some illegal tag closings to make it more HTML5. Since XHTML5 doesn't seems to be well supported
		ret = ret.replaceAll("></IMG>", ">")
		ret = ret.replaceAll("<BR></BR>", "<BR>")
		ret = ret.replaceAll("<HR></HR>", "<HR>")
		ret = ret.replaceAll("></COL>", ">")
		return ret
	}


	private void appendArticleContent() {
		if (!link) return
			if (link == 'null') return
			def parser = new SAXParser()
		def slurper = new XmlSlurper(parser)
		def file = new File("$rootDir/${link}.html")
		if (!file.exists()) {
			log.error("$file not found. Ignoring link. Current article: $title")
			return
		}
		def html = slurper.parse(file)
		
		//Find the <div class="section/>
		GPathResult sectionDiv = html.'**'.findAll { it.@class == 'section'}.first()
		
		sectionDiv.DIV.replaceNode {} //Remove all child <div>s
		
		//Remove page-edit history
		GPathResult[] pageEditSpan = sectionDiv.'**'.findAll { it.@class in ['page-edit', 'page-history']}
		pageEditSpan.each {
			if (it.parent().parent().name() == 'LI') {
				//Sometimes history is in ul/li
				it.parent().parent().replaceNode {}
			} else {//Sometimes it is just plain p
				it.parent().replaceNode {}
			}
			
		}
		
		def imgs = sectionDiv.'**'.findAll { it.name() == 'IMG'}
		processImages(imgs)
			
		writter.write(GPathToString(sectionDiv))

		//throw new RuntimeException()

	}

	private processImages(imgs) {
		imgs.each {img->
			
			String imgSrc = "$outputDir/${img.@src}"
			File imgFile = new File(imgSrc).getCanonicalFile()
			String imgBaseName = imgFile.name
			def exts = ['.svg', '.jpg', '.jpeg', '.png', '.gif']
			//Remove ext (s)
			exts.each { imgBaseName = imgBaseName.replaceAll(it, '') }


			//Find all files which start with the original name in the link
			def fk = new FileNameFinder().getFileNames(imgFile.parent, "$imgBaseName*")
			if (!fk) {
				log.error("${img.@src} doesn't exist")
				return //Continue
			}
			//Take the longest filename like foo.svg.png. Assume it is the most processed one
			def newSrc = new File(fk.max { it.size() }).getCanonicalFile()
			
			if (newSrc == imgFile) return //The same, ignore
			//Relativize the new link			
			Path outputDirPath = Paths.get(outputDir)
			Path newPath = outputDirPath.relativize(newSrc.toPath())
			img.@src =newPath.toString()
			log.debug("Corrected img link $imgFile.name to  $newPath")
		}
	}


	private void processChapter(int deep) {
		if (deep == 0) {//Root chapter
			rootChapterName = link
			log.info("${'-'*10} $title ${'-'*10}")
			filePartCounter =0
			rootChapterCounter++
			openNewWritter()
		}
		log.debug("${' '*deep}$title")
		
		if (chaptersInFilePart > maxSubChaptersInOneFile) {
			chaptersInFilePart = 0
			filePartCounter++
			openNewWritter()
		}
		appendArticleContent()
		processChildChapters(deep+1)
	}

	private void processChildChapters(int deep=0) {
		children.each {
			chaptersInFilePart++
			currentChapter = it
			processChapter(deep)
		}
	}

	void run() {		
		loadToc()
		currentChapter = toc
		processChildChapters()
		closeWritter()
	}



}
new HtmlProcessor("/tmp/work/Documentation/en", "Manual").run()

if (!new File("Manual").exists()) {
	System.err.println "There is no 'Manual' folder in the current directly. Please copy this link into the 'Documentation/en/' of the unity manual"
	return
}

new HtmlProcessor(new File(".").getCanonicalFile().toString(), "Manual").run()
//new Navigator("/tmp/work/Documentation/en", "ScriptReference").run()


println "Done"