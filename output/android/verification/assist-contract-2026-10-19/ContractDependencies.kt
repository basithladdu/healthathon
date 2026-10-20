package `in`.wedevit.care
object BuildConfig { const val CLOUD_AUDIO_ENABLED = false }
data class RecordingConsent(val audioPath:String,val personId:String,val participants:String,val recordingConsentAt:Long,val transcriptionConsentAt:Long=0,val noticeVersion:Int=1)
data class Conversation(val personId:String,val source:String="",val priorities:String="",val participants:String="",val topics:String="",val questions:String="",val nextSteps:String="")
object NoteRules { val labels=listOf("What matters","Who was there","What we discussed","Still to discuss","Next steps"); fun values(d:Conversation)=listOf(d.priorities,d.participants,d.topics,d.questions,d.nextSteps) }
