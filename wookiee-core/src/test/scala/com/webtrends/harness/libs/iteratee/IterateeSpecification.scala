/*
 *  Copyright (c) 2016 Webtrends (http://www.webtrends.com)
 *  See the LICENCE.txt file distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.webtrends.harness.libs.iteratee

import com.webtrends.harness.libs.iteratee.internal.executeFuture
import org.scalatest.{Assertion, MustMatchers, WordSpecLike}

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration, MILLISECONDS, SECONDS}
import scala.util.Try

/**
 * Common functionality for iteratee tests.
 */
trait IterateeSpecification extends MustMatchers {
  self: WordSpecLike =>

  val waitTime: FiniteDuration = Duration(5, SECONDS)
  def await[A](f: Future[A]): A = Await.result(f, waitTime)
  def ready[A](f: Future[A]): Future[A] = Await.ready(f, waitTime)

  def mustTransformTo[E, A](in: E*)(out: A*)(e: Enumeratee[E, A]): Assertion = {
    val f = Future(Enumerator(in: _*) |>>> e &>> Iteratee.getChunks[A])(Execution.defaultExecutionContext).flatMap[List[A]](x => x)(Execution.defaultExecutionContext)
    Await.result(f, Duration.Inf) mustBe List(out: _*)
  }

  def enumeratorChunks[E](e: Enumerator[E]): Future[List[E]] = {
    executeFuture(e |>>> Iteratee.getChunks[E])(Execution.defaultExecutionContext)
  }

  def mustEnumerateTo[E, A](out: A*)(e: Enumerator[E]): Assertion = {
    Await.result(enumeratorChunks(e), Duration.Inf) mustBe List(out: _*)
  }

  def mustPropagateFailure[E](e: Enumerator[E]): Assertion = {
    Try(Await.result(
      e(Cont(_ => throw new RuntimeException())),
      Duration.Inf
    )).isSuccess mustBe false
  }

  /**
   * Convenience function for creating a Done Iteratee that returns the given value
   */
  def done(value: String): Iteratee[String, String] = Done[String, String](value)

  /**
   * Convenience function for an Error Iteratee that contains the given error message
   */
  def error(msg: String): Iteratee[String, String] = Error[String](msg, Input.Empty)

  /**
   * Convenience function for creating a Cont Iteratee that feeds its input to the given function
   */
  def cont(f: String => Iteratee[String, String]): Iteratee[String, String] = {
    Cont[String, String]({
      case Input.El(input: String) => f(input)
      case unrecognized => throw new IllegalArgumentException(s"Unexpected input for Cont iteratee: $unrecognized")
    })
  }

  /**
   * Convenience function for creating the given Iteratee after the given delay
   */
  def delayed(it: => Iteratee[String, String], delay: Duration = Duration(5, MILLISECONDS))(implicit ec: ExecutionContext): Iteratee[String, String] = {
    Iteratee.flatten(timeout(it, delay))
  }

  val timer = new java.util.Timer(true)
  def timeout[A](a: => A, d: Duration)(implicit e: ExecutionContext): Future[A] = {
    val p = Promise[A]()
    timer.schedule(new java.util.TimerTask {
      def run() {
        p.complete(Try(a))
      }
    }, d.toMillis)
    p.future
  }
}
