package us.brut

import java.util.concurrent.Executors

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import us.brut.model.ShortUrl
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import com.microsoft.azure.storage._
import com.microsoft.azure.storage.table._
import com.microsoft.azure.storage.table.TableQuery._

object Boot extends App {
  val conf = ConfigFactory.load()
  // Retrieve storage account from connection-string.
  val storageAccount = CloudStorageAccount.parse(conf.getString("azure.conn-string"))
  // Create the table client.
  val  tableClient : CloudTableClient= storageAccount.createCloudTableClient()
  // Create the table if it doesn't exist.
  val tableName = "urls"
  val cloudTable = new CloudTable(tableName,tableClient)

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("brutus-system")

  // create and start our service actor
  val service = system.actorOf(Props[MyServiceActor], "brutus-service")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = conf.getInt("brutus.http-port"))

  val blockingExecutionContext = {
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))
  }
}
