package us.brut.model

import com.microsoft.azure.storage.table.TableServiceEntity
import spray.json._
import us.brut.SortUrlJsonProtocol

import scala.util.Random


case class ShortUrlCreateForm(url : String, short : Option[String] = Some(""))

case class ShortUrl(short: String, url: String, var hits: Int, ip: String, created: Long) extends EntityBridge {
  import SortUrlJsonProtocol._
  def this(ip: String, url: String, short: String) = this(short, url, 0, ip,  System.currentTimeMillis / 1000)
  def this(ip: String, url: String, short:Option[String]) = this(ip, url, short.getOrElse(UrlGenerator.url()))
  def this(ip: String, url: String, short: String, collision: Boolean) = this(ip, url, UrlGenerator.diff(short))
  def this(short: ShortUrl) = this(short.ip, short.url, UrlGenerator.diff(short.short))
  this.setPartitionKey(short)
  this.setRowKey(short)
  this.setData(this.toJson.toString)
}




object UrlGenerator {
  // Generate a random url of 3 chars
  def url(length : Int = 3) = urlGeneric(length, UrlGenerator.randomDefault)
  // Give me a different one
  def diff(givenUrl : String) = url(givenUrl.length + 1)
  // Different url generator functions are passed in as arguments if needed be
  def urlGeneric(length : Int, f: Int => String) =  f(length)
  // Default generator
  def randomDefault(length: Int) = Random.alphanumeric.take(length).mkString
}