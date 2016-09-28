package microservice.twitter

import java.nio.ByteBuffer
import java.nio.charset.Charset

import akka.util.ByteString

object Framing {
  val SizeBytes = 4
  val AllSizes = 8 * SizeBytes

  private val utf8charset = Charset.forName("UTF-8")

  def createTweet(t: Tweet): ByteString = {
    val idBytes = t.id.getBytes(utf8charset)

    val userId = t.user.map(_.id.getBytes(utf8charset))
    val userName = t.user.map(_.name.getBytes(utf8charset))
    val userScreenName = t.user.map(_.screenName.getBytes(utf8charset))
    val userLang = t.user.map(_.lang.getBytes(utf8charset))

    val createdAtBytes = t.createdAt.getBytes(utf8charset)
    val textBytes = t.text.getBytes(utf8charset)
    val topic = t.topic.map(_.getBytes(utf8charset))

    val userIdLength = userId.map(_.length).getOrElse(0)
    val userNameLength = userName.map(_.length).getOrElse(0)
    val userScreenNameLength = userScreenName.map(_.length).getOrElse(0)
    val userLangLength = userLang.map(_.length).getOrElse(0)
    val topicLength = topic.map(_.length).getOrElse(0)

    val tweetContentLength = idBytes.length + userIdLength + userNameLength + userScreenNameLength +
      userLangLength + createdAtBytes.length + textBytes.length + topicLength

    val totalSize = SizeBytes + AllSizes + tweetContentLength

    val bb = ByteBuffer
      .allocate(totalSize)
      .putInt(totalSize)
      .putInt(idBytes.length).put(idBytes)
      .putInt(userIdLength).put(userId.getOrElse(Array[Byte]()))
      .putInt(userNameLength).put(userName.getOrElse(Array[Byte]()))
      .putInt(userScreenNameLength).put(userScreenName.getOrElse(Array[Byte]()))
      .putInt(userLangLength).put(userLang.getOrElse(Array[Byte]()))
      .putInt(createdAtBytes.length).put(createdAtBytes)
      .putInt(textBytes.length).put(textBytes)
      .putInt(topicLength).put(topic.getOrElse(Array[Byte]()))

    bb.flip()

    ByteString(bb)
  }

  def createFrame(content: String): ByteString = {
    val contentBytes = content.getBytes(utf8charset)

    val bb = ByteBuffer
      .allocate(SizeBytes + contentBytes.length)
      .putInt(contentBytes.length)
      .put(contentBytes)

    bb.flip()

    ByteString(bb)
  }
}

class ReconcileFrames {
  import microservice.twitter.Framing.SizeBytes

  private var buffer = ByteString()

  private var totalTweetSize: Option[Int] = None

  private val fields = "id" :: "userId" :: "userName" :: "userScreenName" ::
    "userLang" :: "createdAt" :: "text" :: "topic" :: Nil

  def apply(fragment: ByteString): List[Tweet] = {
    buffer = buffer ++ fragment
    tryReadContents()
  }

  private def tryReadContents(): List[Tweet] = {
    totalTweetSize match {
      case None ⇒
        if (buffer.size >= SizeBytes) {
          totalTweetSize = Some(buffer.take(SizeBytes).toByteBuffer.getInt)
          buffer = buffer.drop(SizeBytes)
          tryReadContents()
        } else {
          Nil
        }
      case Some(size) ⇒
        if (buffer.size >= size - SizeBytes) {
          tryReadContentsWithNextSize()
        } else {
          Nil
        }
    }
  }

  private def tryReadContentsWithNextSize(): List[Tweet] = {
    val (_, t) = fields.foldLeft((buffer, Tweet())) { (acc, c) ⇒
      val length = buffer.take(SizeBytes).toByteBuffer.getInt
      val context = buffer.slice(SizeBytes, SizeBytes + length).utf8String
      buffer = buffer.drop(SizeBytes + length)

      val updatedT = c match {
        case "id" ⇒ acc._2.copy(id = context)
        case "userId" ⇒ acc._2.copy(user = Some(User(id = context)))
        case "userName" ⇒ acc._2.copy(user = acc._2.user.map(u ⇒ User(u.id, context)))
        case "userScreenName" ⇒ acc._2.copy(user = acc._2.user.map(u ⇒ User(u.id, u.screenName, context)))
        case "userLang" ⇒ acc._2.copy(user = acc._2.user.map(u ⇒ User(u.id, u.screenName, u.screenName, context)))
        case "createdAt" ⇒ acc._2.copy(createdAt = context)
        case "text" ⇒ acc._2.copy(text = context)
        case "topic" ⇒ acc._2.copy(topic = Option(context))
      }

      (buffer, updatedT)
    }

    totalTweetSize = None
    t :: tryReadContents()
  }
}