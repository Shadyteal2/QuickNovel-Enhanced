package com.lagradost.quicknovel

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class WebViewParserEngineTest {
    private fun getParserScript(parserFile: String): String {
        var f = File("app/src/main/assets/parsers/$parserFile")
        if (!f.exists()) {
            f = File("src/main/assets/parsers/$parserFile")
        }
        if (!f.exists()) {
            throw Exception("File not found: ${f.absolutePath}")
        }
        return f.readText()
    }

    private fun getAssetFile(path: String): String {
        var f = File("app/src/main/assets/$path")
        if (!f.exists()) {
            f = File("src/main/assets/$path")
        }
        return f.readText()
    }

    private fun assetExists(path: String): Boolean {
        return File("app/src/main/assets/$path").exists() || File("src/main/assets/$path").exists()
    }

    private fun getParserScriptWithDependencies(parserFile: String): String {
        val script = getParserScript(parserFile)
        
        // Match: class ClassName extends ParentName
        val match = Regex("""class\s+\w+\s+extends\s+(\w+)""").find(script)
        if (match != null) {
            val parentClass = match.groupValues[1]
            if (parentClass != "Parser" && parentClass != "Object") {
                val candidate1 = "$parentClass.js"
                val candidate2 = "${parentClass}Parser.js"
                
                val parentFile = when {
                    assetExists("parsers/$candidate1") -> candidate1
                    assetExists("parsers/$candidate2") -> candidate2
                    else -> null
                }
                
                if (parentFile != null && parentFile != parserFile) {
                    println("WebViewParserEngineTest: Found parent dependency $parentFile for $parserFile. Prepending.")
                    val parentScript = getParserScriptWithDependencies(parentFile)
                    return "$parentScript\n$script"
                }
            }
        }
        return script
    }

    @Test
    fun testFullScriptConcatenation() {
        val bridge = getAssetFile("webtoepub_bridge.js")
        val util = getAssetFile("Util.js")
        val imgur = getAssetFile("Imgur.js")
        val imageCollector = getAssetFile("ImageCollector.js")
        val epubMetaInfo = getAssetFile("EpubMetaInfo.js")
        val parser = getAssetFile("Parser.js")
        val factory = getAssetFile("ParserFactory.js")
        val uiText = getAssetFile("UIText.js")
        
        val coreScripts = "$bridge\n$util\n$imgur\n$imageCollector\n$epubMetaInfo\n$parser\n$factory\n$uiText"
        val parserScript = getParserScriptWithDependencies("AerialrainParser.js")
        
        val orchestration = """
            (async () => {
                try {
                    let parser = parserFactory.fetchByUrl(window.location.href);
                    if (!parser) {
                        AndroidBridge.onError("No parser found for URL: " + window.location.href);
                        return;
                    }
                    let dom = document;
                    let title = parser.extractTitle(dom);
                    let author = parser.extractAuthor(dom);
                    let cover = parser.findCoverImageUrl(dom);
                    let chapters = await parser.getChapterUrls(dom);
                    
                    // Map WebToEpub chapter structure to flat name & url map
                    let chapterList = chapters.map(c => ({
                        name: c.title || c.name || "[No Title]",
                        url: c.sourceUrl || c.url
                    }));
                    
                    AndroidBridge.onMetadataParsed(JSON.stringify({
                        title: title,
                        author: author,
                        cover: cover,
                        chapters: chapterList
                    }));
                } catch (err) {
                    AndroidBridge.onError(err.toString());
                }
            })();
        """.trimIndent()
        
        val fullScript = "$coreScripts\n$parserScript\n$orchestration"
        val lines = fullScript.lines()
        println("Total lines in fullScript: ${lines.size}")
        
        // Write the full script to a scratch file for direct inspect
        val scratchDir = File("build/tmp/scratch")
        scratchDir.mkdirs()
        File(scratchDir, "fullScript.js").writeText(fullScript)
        println("Wrote full script to: ${File(scratchDir, "fullScript.js").absolutePath}")
    }
}
