@Grab(group='com.belerweb', module='pinyin4j', version='2.5.1')
import net.sourceforge.pinyin4j.PinyinHelper
import java.text.SimpleDateFormat

def sdf = new SimpleDateFormat('yyyyMMdd')
def sinceDate
def untilDate

try {
    sinceDate = sdf.parse(args[0])
    untilDate = sdf.parse(args[1])
} catch (e) {
    println "Usage: groovy GitStats.groovy <begin(yyyyMMdd)> <end(yyyyMMdd)>"
    return
}

def generateGitLog(Date since, Date until) {
    def sdf = new SimpleDateFormat('yyyy-MM-dd')
    def gitLogFile = File.createTempFile("gitstats_${System.nanoTime()}", ".log")
    def cmdLine = """cmd /c "git fetch && git log --shortstat --date-order --pretty=format:%s###%aN###%ae###%ai --since=${sdf.format(since)} --until=${sdf.format(until)} > ${gitLogFile.absolutePath}" """
    cmdLine.execute().waitFor()
    return gitLogFile
}

def toPinyin = { String str ->
    StringBuilder sb = new StringBuilder()
    for (char c : str) {
        String[] py = PinyinHelper.toHanyuPinyinStringArray(c)
        sb << (py ? py[0] : c)
    }
    return sb.toString()
}

def gitLogFile = generateGitLog(sinceDate, untilDate)
def content = gitLogFile.getText('UTF-8')
def commitInfoList = content.findAll(/(?s)([^\r\n]+?)###([a-zA-Z_.\d\u4e00-\u9fa5- ]+)###([a-zA-Z_.\d-]+@[a-zA-Z_.\d-]+)###(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+-]\d{4})\r?\n\s+(\d+)\s+file(?:s)? changed(?:,\s+(\d+)\s+insertion(?:s)?\([+]\))?(?:,\s+(\d+)\s+deletion(?:s)?\(-\))?\r?\n/) { _0, _1, _2, _3, _4, _5, _6, _7 ->    
    def commitInfo = new CommitInfo(
        subject: _1,
        author: toPinyin(_2),
        email: _3,
        date: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss Z').parse(_4),
        fileChanged: (_5?:'0') as int,
        insertions: (_6?:'0') as int,
        deletions: (_7?:'0') as int
    )
    
    return commitInfo
}

def result = GQ {
    from c in commitInfoList
    groupby c.email
    orderby count() in desc
    select max(c.author) as author, c.email, count() as sumCommits, sum(c.fileChanged) as sumFilesChanged, sum(c.insertions) as sumInsertions, sum(c.deletions) as sumDeletions
}

print result

def mal = GQ {
    from r in result
    select max(r.author.length()) as malOfAuthor, max(r.email.length()) as malOfEmail
}.stream().findFirst().get()

def sumResult = GQ {
    from r in result
    select ' ' * mal.malOfAuthor as author, ' ' * mal.malOfEmail as email, sum(r.sumCommits) as sumCommits, sum(r.sumFilesChanged) as sumFilesChanged, sum(r.sumInsertions) as sumInsertions, sum(r.sumDeletions) as sumDeletions
}
print sumResult

@groovy.transform.ToString
class CommitInfo {
    String subject
    String author
    String email
    Date date
    int fileChanged
    int insertions
    int deletions
}
