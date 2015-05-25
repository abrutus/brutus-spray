package us.brut.persistance

import java.util.concurrent.Executors

import us.brut.model.ShortUrl
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.table.{TableOperation, CloudTable, CloudTableClient}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

trait Backend[T] {
  def get(key:String) : Future[T]
  def set(value: T) : Future[T]
}
