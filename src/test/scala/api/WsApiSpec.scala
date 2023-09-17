package api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.syntax.all._
import org.mockito.Mockito._
import services.CollatzMachineService
import org.typelevel.log4cats.Logger
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.websocket._
import org.http4s.jdkhttpclient._
import org.http4s.Uri
import java.net.http.HttpClient
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.typelevel.log4cats.slf4j.Slf4jLogger

class WsApiSpec extends AsyncFreeSpec with MockitoSugar with Matchers {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val testService: CollatzMachineService[IO] = mock[CollatzMachineService[IO]]
  private val wsApi = new WsApiLive[IO](testService)
  private val serverResource = BlazeServerBuilder[IO]
    .bindHttp(6000, "localhost")
    .withHttpWebSocketApp(wsb => wsApi.routes(wsb).orNotFound)
    .resource

  private val wsClient: WSClient[IO] = IO(HttpClient.newHttpClient())
    .map(JdkWSClient[IO])
    .unsafeRunSync()

  private def testWebSocket(uri: Uri, expectedMessages: List[String]) =
    wsClient
      .connectHighLevel(WSRequest(uri))
      .use { conn =>
        val receiveStream = conn.receiveStream.collect {
          case WSFrame.Text(text, _) => text
        }.take(expectedMessages.size).compile.toList

        for {
          receivedMessages <- receiveStream
          _ = receivedMessages should contain theSameElementsInOrderAs expectedMessages
        } yield succeed
      }

  "WebSocket /messages/:id" - {
    "should receive expected updates for a machine" in {
      when(testService.singleMachineUpdates("1")).thenReturn(IO.pure(Stream.emits(List(1, 2, 3)).covary[IO]))

      serverResource.use { _ =>
        testWebSocket(uri"ws://localhost:6000/messages/1", List("1", "2", "3"))
      }.unsafeToFuture()
    }
  }

  "WebSocket /messages" - {
    "should receive expected updates for all machines" in {
      val testUpdates = List((1, 1), (2, 2), (3, 3))
      when(testService.allMachinesUpdates()).thenReturn(IO.pure(Stream.emits(testUpdates).covary[IO]))

      serverResource.use { _ =>
        testWebSocket(uri"ws://localhost:6000/messages", List("Machine 1: 1", "Machine 2: 2", "Machine 3: 3"))
      }.unsafeToFuture()
    }
  }
}
