package us.brut

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._

class MyServiceSpec extends Specification with Specs2RouteTest {
  def actorRefFactory = system
  
  "MyService" should {

  }
}
