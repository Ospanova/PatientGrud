package mainn

import model.{FullPatient, Patient}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import mappings.JsonMappings
import akka.http.scaladsl.server.directives.MethodDirectives.{delete, get, post, put}

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout
import Actors.PatientActor._
import Actors.{PatientActor, Patients}
import RabbitQ.{Recv, SendRabbit}
import akka.io.Udp.Send
import dao.PatientDAO

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


class Router(implicit val system: ActorSystem, val patientActor: ActorRef) extends JsonMappings {
    // Required by the `ask` (?) method below
    implicit lazy val timeout: Timeout = Timeout(10.seconds) // usually we'd obtain the timeout from the system's configuration

    implicit def myExceptionHandler: ExceptionHandler = ExceptionHandler {
        case _: Exception =>
            /*extractUri { uri =>
              println(s"Request to $uri could not be handled normally")
              complete((StatusCodes.BadRequest, ""))
            }*/
            complete((StatusCodes.BadRequest, ""))
    }

    val route: Route =
        path("patients") {
            get {
                val p = (patientActor ? GetPatients).mapTo[Patients]
                onSuccess(p) { performed =>
                    complete(s"${performed}")
                }
            } ~
            post {
                entity(as[FullPatient]) { p =>
                  val patient = (patientActor ? AddPatient(p)).mapTo[ActionPerformed]
                  onSuccess(patient) { performed =>
                    complete(s"OK ${performed}")
                  }
                }
              }
        } ~
          (path("patients"/IntNumber) & get) { id =>
                  get {
                      println(s"we are going to get user with id ${id}")
                      val patient = (patientActor ? GetbyID(id)).mapTo[Option[Patient]]
                      rejectEmptyResponse{
                          complete(patient)
                      }
                  }
          } ~
          (path("patients"/IntNumber) & put) { id => entity(as[Patient]) { p =>
                println(s"we are going to update user with id ${id}")
                val patient = (patientActor ? UpdatePatient(id, p)).mapTo[ActionPerformed]
                onSuccess(patient) { performed =>
                complete(s"Updated")
                }
          } } ~
            (path("patients"/IntNumber) & delete) { id =>
                println(s"we are going to delete user with id ${id}")
                val patient = (patientActor ? DeletePatient(id)).mapTo[ActionPerformed]
                onSuccess(patient) { performed =>
                    complete(s"Deleted")
                }
            }
}
object  Boot extends App {

//    val patientDao = new PatientDAO
//    patientDao.addPatient(FullPatient("dfghj", "ghjkl", "Ghjk", "ghjkl"))

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val pActor: ActorRef = system.actorOf(PatientActor.props, "PatientActor")

    val patientRoute = new Router()
    val bindingFuture = Http().bindAndHandle(patientRoute.route, "localhost", 8080)



    val sendCl = SendRabbit("""{"action":"getByID","id": 5}""")
    sendCl.sendMsg()
    //Thread.sleep(1000)
    val rcv = Recv()
    rcv.recv()


}
