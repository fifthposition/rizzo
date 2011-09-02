import java.text.SimpleDateFormat
import groovy.text.SimpleTemplateEngine
import com.petebevin.markdown.MarkdownProcessor

@Grab('com.madgag:markdownj-core:0.4.1')

class Post {
	String title
	String name
	Date dateCreated
	Date lastUpdated
	String summary
	String content
	List tags = []
}

class Tag {
	String name
	List posts = []	
	String toString(){ name }
}

def cl = new CliBuilder(usage: 'groovy rizzo -s "source" -d "destination"')

cl.s(longOpt:'source', args:1, required:true, 'Location of website source')
cl.d(longOpt:'destination', args:1, required:true, 'Location in which to place generated website')

def opt = cl.parse(args)

if(!opt){
	return
} else {
    def posts = []
    def tags = []
    SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy h:mm a")
    SimpleDateFormat outputFormatter = new SimpleDateFormat("EEEE, MMMMM d, yyyy 'at' h:mm a")
    SimpleDateFormat archiveFormatter = new SimpleDateFormat("MMMMM d, yyyy")
    SimpleDateFormat feedFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    SimpleDateFormat itemIdDateFormatter = new SimpleDateFormat("yyyy-MM-dd")

    def sourceExists = new File("${opt.d}").exists()
    def siteConfig = new ConfigSlurper().parse(new File("${opt.s}/site-config.groovy").toURL())
    def metadata

    if(!new File("${opt.s}/meta.groovy").exists()){
	    new File("${opt.s}/meta.groovy").write("published = \"${formatter.format(new Date())}\"")
    }
    metadata = new ConfigSlurper().parse(new File("${opt.s}/meta.groovy").toURL())

    Date lastPublished = formatter.parse(metadata.published)

    def begin = "${opt.s}/posts/"; def h_begin = "${opt.s}/pages/"

    def home = new File("${opt.s}/templates/home.html")
    def home_mid = new File("${opt.s}/templates/home_mid.html")
    def page = new File("${opt.s}/templates/page.html")
    def post = new File("${opt.s}/templates/post.html")
    def feed = new File("${opt.s}/templates/feed.xml"); def entry = new File("${opt.s}/templates/entry.xml")

	def fozziwig = new SimpleTemplateEngine()
	def mdProcessor = new MarkdownProcessor()
	
	new File("${opt.d}/archives/").mkdirs()
	
	new File("${h_begin}").eachFile { file ->
	    if(file.name.endsWith('.html')) {
	        def name = file.name[0 .. file.name.lastIndexOf('.')-1]
            List pageText = file.readLines()
            def currentPost = new Post(title:pageText[0], name:name, lastUpdated:formatter.parse(pageText[1].toString()))
	        pageText = pageText[2..-1]
		    currentPost.content = pageText.join("\n")
    		if(currentPost.lastUpdated > lastPublished || !sourceExists){
	    		def scrooge = ["postTitle" : currentPost.title, "postName" : currentPost.name, "siteName" : siteConfig.site.name, "content" : currentPost.content, "authorName" : siteConfig.author.name, "lastUpdated" : """
			<br />
	        <p style="font-size:smaller; text-align:right;">(Updated ${outputFormatter.format(currentPost.lastUpdated)})</p>"""]
		    	new File("${opt.d}/${name}.html").write("${fozziwig.createTemplate(page).make(scrooge)}")
            }
        }
    }

	new File("${h_begin}").eachFile { file ->
	    if(file.name.endsWith('.md')) {
	        def name = file.name[0 .. file.name.lastIndexOf('.')-1]
            List pageText = file.readLines()
            def currentPost = new Post(title:pageText[0], name:name, lastUpdated:formatter.parse(pageText[1].toString()))
	        pageText = pageText[2..-1]
		    currentPost.content = mdProcessor.markdown(pageText.join("\n"))
    		if(currentPost.lastUpdated > lastPublished || !sourceExists){
	    		def scrooge = ["postTitle" : currentPost.title, "postName" : currentPost.name, "siteName" : siteConfig.site.name, "content" : currentPost.content, "authorName" : siteConfig.author.name, "lastUpdated" : """
			<br />
	        <p style="font-size:smaller; text-align:right;">(Updated ${outputFormatter.format(currentPost.lastUpdated)})</p>"""]
		    	new File("${opt.d}/${name}.html").write("${fozziwig.createTemplate(page).make(scrooge)}")
            }
        }
    }

	new File("${begin}").eachFile { file ->
	    if(file.name.endsWith('.html')) {
    	    def name = file.name[0 .. file.name.lastIndexOf('.')-1]
            List postText = file.readLines()
            def currentPost = new Post(title:postText[0], name:name, dateCreated:formatter.parse(postText[1].toString()), lastUpdated:formatter.parse(postText[2].toString()), summary:postText[4])
            List tagList = postText[3].split(", ") as List
            tagList.each { currentPost.tags << new Tag(name:"$it") }
            postText = postText[5..-1]
	        currentPost.content = postText.join("\n")
	        posts << currentPost
    	    def postTags = null
            if(!currentPost.tags.isEmpty()){
                postTags = "; "
                postTags += currentPost.tags.sort{it.name}.collect{"<a href=\"/tags/${it.name}.html\">${it.name}</a>"}.join(", ")
            }
            currentPost.tags.each { postTag ->
                def currentTag
                if(!tags.find{it.name.contains("$postTag")}){
                    currentTag = new Tag(name:postTag)
                    currentTag.posts << currentPost
	                tags << currentTag
                } else {
	                currentTag = tags.find{it.name.contains("$postTag")}
	                currentTag.posts << currentPost
                }
            }
            if(currentPost.lastUpdated > lastPublished || !sourceExists){
                def scrooge = ["postTitle" : currentPost.title, "postName" : currentPost.name, "postDate" : outputFormatter.format(currentPost.dateCreated), "siteName" : siteConfig.site.name, "postTags" : postTags ?: "", "content" : currentPost.content, "authorName" : siteConfig.author.name]
                new File("${opt.d}/archives/${currentPost.name}.html").write("${fozziwig.createTemplate(post).make(scrooge)}")
            }
        }
    }

	new File("${begin}").eachFile { file ->
	    if(file.name.endsWith('.md')) {
		    def name = file.name[0 .. file.name.lastIndexOf('.')-1]
    	    List postText = file.readLines()
	        def currentPost = new Post(title:postText[0], name:name, dateCreated:formatter.parse(postText[1].toString()), lastUpdated:formatter.parse(postText[2].toString()), summary:postText[4])
	        List tagList = postText[3].split(", ") as List
    	    tagList.each { currentPost.tags << new Tag(name:"$it") }
	        postText = postText[5..-1]
	        currentPost.content = mdProcessor.markdown(postText.join("\n"))
    	    posts << currentPost
	        def postTags = null
	        if(!currentPost.tags.isEmpty()){
    	        postTags = "; "
	            postTags += currentPost.tags.sort{it.name}.collect{"<a href=\"/tags/${it.name}.html\">${it.name}</a>"}.join(", ")
    	    }
	        currentPost.tags.each { postTag ->
	            def currentTag
	            if(!tags.find{it.name.contains("$postTag")}){
    	            currentTag = new Tag(name:postTag)
	                currentTag.posts << currentPost
	                tags << currentTag
	            } else {
    	            currentTag = tags.find{it.name.contains("$postTag")}
	                currentTag.posts << currentPost
	            }
    	    }
	        if(currentPost.lastUpdated > lastPublished || !sourceExists){
	            def scrooge = ["postTitle" : currentPost.title, "postName" : currentPost.name, "postDate" : outputFormatter.format(currentPost.dateCreated), "siteName" : siteConfig.site.name, "postTags" : postTags ?: "", "content" : currentPost.content, "authorName" : siteConfig.author.name]
	            new File("${opt.d}/archives/${currentPost.name}.html").write("${fozziwig.createTemplate(post).make(scrooge)}")
    	    }
    	}
    }

    def gonzo = new AntBuilder()
    new File("${opt.d}/css/").mkdirs()
    gonzo.copy(todir: "${opt.d}/css/") {
	    fileset(dir : "${opt.s}/css/")
    }

    new File("${opt.d}/images/").mkdirs()
    gonzo.copy(todir: "${opt.d}/images/") {
    	fileset(dir : "${opt.s}/images/")
    }

    new File("${opt.d}/tags/").mkdirs()
    tags.each{ tag ->
	    tag.posts = tag.posts.sort{ it.dateCreated }.reverse()
        String tagMid = """
	             <p></p>
	             <table>
	    """
	    tag.posts.each { currentPost ->
	    	tagMid += """
				     <tr>
	                     <td valign="top" class="date"><span class="arc_date">${archiveFormatter.format(currentPost.dateCreated)}</span></td>
	                     <td valign="top"><a href="/archives/${currentPost.name}.html">${currentPost.title}</a></td>
	                 </tr>
	    	"""
	    }
		tagMid += """
	             </table>
	    """
	
	    def dickens = ["postTitle" : "Archives for &ldquo;${tag.name}&rdquo;", "postName" : tag.name, "siteName" : siteConfig.site.name, "postUpdate" : "", "authorName" : siteConfig.author.name, "content" : tagMid]
	
        new File("${opt.d}/tags/${tag.name}.html").write("${fozziwig.createTemplate(page).make(dickens)}")
        
        def max = tag.posts.size() > 20 ? 19 : tag.posts.size() - 1
		def tagFeed = new File("${opt.d}/tags/${tag.name}.xml")
		String entries = ""
		tag.posts[0..max].each { cp ->
		        def itemIdDate = itemIdDateFormatter.format(cp.dateCreated)
			    def cratchit = ["postTitle" : cp.title, "postLink" : "http://${siteConfig.site.domain}/archives/${cp.name}.html", "postSummary" : cp.summary, "postContent" : cp.content, "itemId" : "tag:${siteConfig.site.domain},${itemIdDate}:/${cp.name}/", "itemDate" : feedFormatter.format(cp.dateCreated), "itemUpdatedDate" : feedFormatter.format(cp.lastUpdated)]
                entries += "${fozziwig.createTemplate(entry).make(cratchit)}"
		}
		def tim = ["siteDomain" : siteConfig.site.domain, "feedUrl" : "http://${siteConfig.site.domain}/tags/${tag.name}.xml", "tagName" : tag.name, "feedTitle" : "${siteConfig.site.name} : ${tag.name}", "siteName" : siteConfig.site.name, "lastUpdated" : feedFormatter.format(new Date()), "authorName" : siteConfig.author.name, "authorEmail" : siteConfig.author.email, "entries" : entries]
		tagFeed.write("${fozziwig.createTemplate(feed).make(tim)}")
    }

    posts = posts.sort{ it.dateCreated }.reverse()
    File rootIndex = new File("${opt.d}/index.html")
    String homeContent = ""
    def max = posts.size() > 5 ? 4 : posts.size() - 1
    posts[0..max].each { currentPost ->
	    def postTags = null
        if(!currentPost.tags.isEmpty()){
            postTags = "; "
            postTags += currentPost.tags.sort{it.name}.collect{"<a href=\"/${it.name}/\">${it.name}</a>"}.join(", ")
        }
        def pd = ["postTitle" : currentPost.title, "postLink" : "http://${siteConfig.site.domain}/archives/${currentPost.name}.html", "postDate" : outputFormatter.format(currentPost.dateCreated), "postTags" : postTags ?: "", "content" : currentPost.content]
        homeContent += fozziwig.createTemplate(home_mid).make(pd)
    }

    def fred = ["siteName" : siteConfig.site.name, "content" : homeContent, "authorName" : siteConfig.author.name]
    rootIndex.write("${fozziwig.createTemplate(home).make(fred)}")
    String tagList = "<p>"
    tagList += tags.sort{it.name}.collect{"<a href=\"/${it.name}/\">${it.name}</a>&nbsp;(${it.posts.size()})"}.join(" &nbsp; &nbsp; ")
    tagList += "</p>"
    def thingsNSuch = ["postTitle" : "Tags", "postName" : "tags", "siteName" : siteConfig.site.name, "authorName" : siteConfig.author.name, "postUpdate" : "", "content" : tagList]
    new File("${opt.d}/tags.html").write("${fozziwig.createTemplate(page).make(thingsNSuch)}")
    new File("${opt.d}/archives/").mkdirs()
    File arcIndex = new File("${opt.d}/archives.html")
    String archiveContent = "<table>"
    posts.each { currentPost ->
    	archiveContent += """
			     <tr>
                     <td valign="top" class="date"><span class="arc_date">${archiveFormatter.format(currentPost.dateCreated)}</span></td>
                     <td valign="top"><a href="/archives/${currentPost.name}.html">${currentPost.title}</a></td>
                 </tr>
    	"""
    }

    archiveContent += """
             <p></p>
             <table>
    """
    def archive = ["postTitle" : "Archives", "postName" : "archives", "siteName" : siteConfig.site.name, "postUpdate" : "", "content" : archiveContent, "authorName" : siteConfig.author.name]
    arcIndex.write("${fozziwig.createTemplate(page).make(archive)}")
    def siteFeed = new File("${opt.d}/feed.xml")
    max = posts.size() > 20 ? 19 : posts.size() - 1
    String feedEntries = ""
	posts[0..max].each { currentPost ->
		def itemIdDate = itemIdDateFormatter.format(currentPost.dateCreated)
		def cratchit = ["postTitle" : currentPost.title, "postSummary" : currentPost.summary, "postContent" : currentPost.content, "itemId" : "tag:${siteConfig.site.domain},${itemIdDate}:/archives/${currentPost.name}.html", "postLink" : "http://${siteConfig.site.domain}/archives/${currentPost.name}.html", "itemDate" : feedFormatter.format(currentPost.dateCreated), "itemUpdatedDate" : feedFormatter.format(currentPost.lastUpdated)]
        feedEntries += "${fozziwig.createTemplate(entry).make(cratchit)}"
    }

    def feedBits = ["feedUrl" : "http://${siteConfig.site.domain}/feed.xml", "feedTitle" : siteConfig.site.name, "lastUpdated" : feedFormatter.format(new Date()), "authorName" : siteConfig.author.name, "authorEmail" : siteConfig.author.email, "entries" : feedEntries, "siteDomain" : siteConfig.site.domain]
    siteFeed.write("${fozziwig.createTemplate(feed).make(feedBits)}")

    new File("${opt.s}/meta.groovy").delete()
    new File("${opt.s}/meta.groovy").write("published = \"${formatter.format(new Date())}\"")

  }