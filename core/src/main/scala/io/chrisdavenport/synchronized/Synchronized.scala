/*
 * Copyright 2018-2019 OVO Energy Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * Modifications 
 * Copyright (C) 2019 Christopher Davenport
 * Edits: 
 * 1. Change Package
 * 2. Remove Private Status
 * 3. Switch from fine grained to bulk imports
 * 4. Private Class And For Comprehension Clarity
 * 5. Switched to Simple Semaphore Approach
 */
package io.chrisdavenport.synchronized

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._

/**
  * Provides synchronized access to a resource `A`, similar to that of
  * `synchronized(a) { use(a) }`, except the blocking is semantic only,
  * and no actual threads are blocked by the implementation.
  */
object Synchronized {
  def apply[F[_]](implicit F: Concurrent[F]): ApplyBuilders[F] =
    new ApplyBuilders(F)

  def in[G[_]: Sync, F[_]: Concurrent, A](a: A): G[Resource[F, A]] =
    Semaphore.in[G, F](1).map(sem => 
      Resource.make(sem.acquire)(_ => sem.release).as(a)
    )

  def of[F[_]: Concurrent, A](a: A) = in[F, F, A](a)

  def uncancelableIn[G[_]: Sync, F[_]: Async, A](a: A): G[Resource[F, A]] = 
    Semaphore.uncancelableIn[G, F](1).map(sem => 
      Resource.make(sem.acquire)(_ => sem.release).as(a)
    )

  def uncancelable[F[_]: Async, A](a: A): F[Resource[F, A]] =
    uncancelableIn[F, F, A](a)

  final class ApplyBuilders[F[_]](private val F: Concurrent[F]) extends AnyVal {
    def of[A](a: A): F[Resource[F, A]] =
      Synchronized.of(a)(F)
  }
}