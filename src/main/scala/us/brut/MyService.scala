package us.brut

import java.lang.Throwable
import java.util.concurrent.Executors

import akka.actor.{Props, Actor}
import akka.util.Timeout
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.table.{TableServiceException, TableOperation, CloudTable, CloudTableClient}
import com.typesafe.config.ConfigFactory
import spray.caching.{LruCache, Cache}
import spray.json.DefaultJsonProtocol
import spray.routing._
import spray.http._
import spray.httpx.SprayJsonSupport._
import us.brut.model.{ShortUrl, ShortUrlCreateForm, EntityBridge}
import us.brut.persistance.Backend
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


import scala.util.{Try, Failure, Success}

object SortUrlJsonProtocol extends DefaultJsonProtocol {
  implicit val colorFormat = jsonFormat5(ShortUrl.apply)
  implicit val unmarshaller = jsonFormat2(ShortUrlCreateForm)
}
class MyServiceActor extends Actor with HttpService {
  val cache: Cache[ShortUrl] = LruCache()

  import spray.http._

  def actorRefFactory = context

  implicit val timeout = Timeout(5 seconds)

  import SortUrlJsonProtocol.unmarshaller

  val routes =
    pathPrefix("static") {
      get {
        getFromResourceDirectory("static")
      }
    } ~
      path("favicon.ico") { getFromResource("static/favicon.ico") } ~
      path("") { getFromResource("static/index.html") } ~
      path("create") {
        clientIP { ip =>
          post {
            entity(as[ShortUrlCreateForm]) {
              form => {
                import SortUrlJsonProtocol.colorFormat
                import context._
                onComplete(Azure.set(new ShortUrl(ip.toString, form.url, form.short))) {
                  case Success(value: ShortUrl) => complete(value)
                  case Failure(tse: TableServiceException) =>
                    if (tse.getErrorCode == Azure.errorConflict)
                      complete(StatusCodes.Conflict, "URL collision")
                    else
                      complete(StatusCodes.InternalServerError, tse.getMessage)
                  case Failure(t: Throwable) => complete(StatusCodes.InternalServerError, t.getMessage)
                }
              }
            }
          }
        }
      } ~
      path(Rest) {
          attemptedURL =>
            get {
              import SortUrlJsonProtocol.colorFormat
              import context._
              onComplete(cache(attemptedURL) {Azure.get(attemptedURL)}) {
                case Success(value: ShortUrl) => redirect(value.url, StatusCodes.PermanentRedirect)
                case Failure(ex: Azure.NotFound) => complete(StatusCodes.NotFound, "Resource not found")
                case Failure(e) =>  complete(StatusCodes.InternalServerError, e.getMessage)
              }
            }
        }



  def receive = runRoute(routes)
}

object Azure extends Backend[ShortUrl]{
  val errorConflict = "EntityAlreadyExists"
  val conf = ConfigFactory.load()
  // Retrieve storage account from connection-string.
  val storageAccount = CloudStorageAccount.parse(conf.getString("azure.conn-string"))
  // Create the table client.
  val  tableClient : CloudTableClient= storageAccount.createCloudTableClient()
  val tableName = "urls"
  val cloudTable = new CloudTable(tableName,tableClient)

  def insert(x:ShortUrl) : ShortUrl = {
    cloudTable.execute(TableOperation.insert(x))
    x
  }
  case class NotFound(message:String="404") extends Exception

  implicit val blockingExecutionContext =  ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))
  override def get(key: String): Future[ShortUrl] = {
    println(s"Trying to fetch {$key} from azure")
    import spray.json._
    import  SortUrlJsonProtocol._
    Future {
      val retrieveOp = TableOperation.retrieve(key, key, classOf[EntityBridge])
      val specificEntity : EntityBridge= cloudTable.execute(retrieveOp).getResultAsType()
      if (specificEntity == null) throw NotFound()
      specificEntity.getData.parseJson.convertTo[ShortUrl]
    }(blockingExecutionContext)
  }

  override def set(shortUrl:ShortUrl) = setWrapper(shortUrl)
  def setWrapper(shortUrl: ShortUrl): Future[ShortUrl] = Future(setBlocking(shortUrl,0))(blockingExecutionContext)
  def setBlocking(shortUrl: ShortUrl, tries: Int = 0): ShortUrl =
    Try(insert(shortUrl)) match {
      case Success(value: ShortUrl) => value
      case Failure(tse: TableServiceException) =>
        if (tse.getErrorCode == Azure.errorConflict && tries<3)
          setBlocking(new ShortUrl(shortUrl), tries +1)
        else throw tse
      case Failure(t: Throwable) => throw t
    }
}
