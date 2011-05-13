import java.text.SimpleDateFormat

class Post{
	String title
	String name
	Date dateCreated
	Date lastUpdated
	String summary
	String intro
	String content
}

def cl = new CliBuilder(usage: 'groovy rizzo -s "source" -d "destination"')

cl.s(longOpt:'source', args:1, required:true, 'Location of website source')
cl.d(longOpt:'destination', args:1, required:true, 'Location in which to place generated website')
def opt = cl.parse(args)
if(!opt){
	return
} else {

    def posts = []
    def siteConfig = new ConfigSlurper().parse(new File("${opt.s}/site-config.groovy").toURL())

    def begin = "${opt.s}/posts/"; def h_begin = "${opt.s}/pages/"

    def page_head = new File("${opt.s}/templates/page_head.html").getText()
    def page_foot = new File("${opt.s}/templates/page_foot.html").getText()
    def home_head = new File("${opt.s}/templates/home_head.html").getText()
    def home_mid = new File("${opt.s}/templates/home_mid.html").getText()
    def home_foot = new File("${opt.s}/templates/home_foot.html").getText()
    def post_head = new File("${opt.s}/templates/post_head.html").getText()
    def post_foot = new File("${opt.s}/templates/post_foot.html").getText()

    new File("${h_begin}").eachFileMatch(~/.*\.html/){ file ->
	    def name = file.name[0 .. file.name.lastIndexOf('.')-1]
    	new File("${opt.d}/${name}/").mkdirs()
        def config = new ConfigSlurper().parse(new File("${h_begin}/${name}.groovy").toURL())
    	def currentPost = new Post(title:config.title, name:name, summary:config.summary, content:file.text)

    	println "Generating ${currentPost.name} page..."

        File index = new File("${opt.d}/${currentPost.name}/index.html")
        index.write(page_head.replaceAll(/POST_TITLE/, "${currentPost.title}").replaceAll(/POST_NAME/, "${currentPost.name}"))
        index.append(currentPost.content)
        index.append(page_foot.replaceAll(/POST_TITLE/, "${currentPost.title}").replaceAll(/POST_NAME/, "${currentPost.name}"))
    }

	SimpleDateFormat formatter = new SimpleDateFormat("EEEE, MMMMM d, yyyy 'at' h:mm a")

    new File("${begin}").eachFileMatch(~/.*\.html/){ file ->
    	def name = file.name[0 .. file.name.lastIndexOf('.')-1]
    	new File("${opt.d}/blog/${name}/").mkdirs()
        def config = new ConfigSlurper().parse(new File("${begin}/${name}.groovy").toURL())
    	def currentPost = new Post(title:config.title, name:name, dateCreated:formatter.parse(config.date.toString()), lastUpdated:formatter.parse(config.updated.toString()), summary:config.summary, intro:config.intro, content:file.text)
    	println "Generating post \"${currentPost.name}\"..."

    	posts << currentPost

        File index = new File("${opt.d}/blog/${currentPost.name}/index.html")
    	index.write(post_head.replaceAll(/POST_TITLE/, "${currentPost.title}").replaceAll(/POST_NAME/, "${currentPost.name}").replaceAll(/POST_DATE/, "${formatter.format(currentPost.dateCreated)}"))
        index.append(currentPost.content)
        index.append(post_foot.replaceAll(/POST_TITLE/, "${currentPost.title}").replaceAll(/POST_NAME/, "${currentPost.name}"))
    }

    /** copy CSS unmodified **/
    new File("${opt.d}/css/").mkdirs()
    new AntBuilder().copy(todir: "${opt.d}/css/") {
	    fileset(dir : "${opt.s}/css/")
    }

    /** likewise images **/
    new File("${opt.d}/images/").mkdirs()
    new AntBuilder().copy(todir: "${opt.d}/images/") {
    	fileset(dir : "${opt.s}/images/")
    }

    println "Generating index page..."

    posts = posts.sort{ it.dateCreated }.reverse()

    File rootIndex = new File("${opt.d}/index.html")
    rootIndex.write(home_head)

    def max = posts.size() > 5 ? 4 : posts.size() - 1

    posts[0..max].each { currentPost ->
        def appendage = home_mid.replaceAll(/POST_TITLE/, currentPost.title).replaceAll(/POST_NAME/, currentPost.name).replaceAll(/POST_DATE/, formatter.format(currentPost.dateCreated))
        appendage = appendage.replaceAll(/POST_INTRO/, currentPost.intro)
        rootIndex.append(appendage)
    }

    rootIndex.append(home_foot)

    println "Generating archives page..."

    SimpleDateFormat archiveFormatter = new SimpleDateFormat("MMMMM d, yyyy")

    new File("${opt.d}/archives/").mkdirs()
    File arcIndex = new File("${opt.d}/archives/index.html")
    arcIndex.write(page_head.replaceAll(/POST_TITLE/, "Archives").replaceAll(/POST_NAME/, "archives"))

    arcIndex.append("""
             <p></p>
             <table>
    """)

    posts.each { currentPost ->
    	arcIndex.append("""
			     <tr>
                     <td valign="top" class="date"><span class="arc_date">${archiveFormatter.format(currentPost.dateCreated)}</span></td>
                     <td valign="top"><a href="/blog/${currentPost.name}/">${currentPost.title}</a></td>
                 </tr>
    	""")	
    }

    arcIndex.append("""
             </table>
    """)

    arcIndex.append(page_foot)

    println "Generating Atom feed..."

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    new File("${opt.d}/feed/").mkdirs()

    def feed = new File("${opt.d}/feed/index.xml").write("""<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <id>http://${siteConfig.site.domain}/feed/</id>
  <title>${siteConfig.site.name}</title>
  <updated>${sdf.format(new Date())}</updated>
  <link rel="self" href="http://${siteConfig.site.domain}/feed/" type="application/atom+xml" />
  <author>
    <name>${siteConfig.author.name}</name>
    <uri>http://${siteConfig.site.domain}</uri>
    <email>${siteConfig.author.email}</email>
  </author>""")

    max = posts.size() > 20 ? 19 : posts.size() - 1

    posts[0..max].each { currentPost ->
        def itemDate = sdf.format(currentPost.dateCreated); def itemUpdatedDate = sdf.format(currentPost.lastUpdated)
        SimpleDateFormat itemIdDateFormatter = new SimpleDateFormat("yyyy-MM-dd")
        def itemIdDate = itemIdDateFormatter.format(currentPost.dateCreated)
        def itemId = "tag:${siteConfig.site.domain},${itemIdDate}:/${currentPost.name}/"
        def feed_item = """
	<entry>
	    <title>${currentPost.title}</title>
	    <id>${itemId}</id>
	    <published>${itemDate}</published>
	    <updated>${itemUpdatedDate}</updated>
	    <link href="http://${siteConfig.site.domain}/${currentPost.name}/"/>
	    <summary>${currentPost.summary}</summary>
	    <content type="html">${currentPost.content.replaceAll("<", "&lt;").replaceAll(">", "&gt;")}
	    </content>
    </entry>"""
        new File("${opt.d}/feed/index.xml").append("${feed_item}")
    }

    new File("${opt.d}/feed/index.xml").append("""</feed>""")

    println "Site published \n"

}