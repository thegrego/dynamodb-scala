package com.onzo.dynamodb

import java.math.MathContext
import java.util.UUID

import cats.Monad
import com.amazonaws.services.dynamodbv2.model.AttributeValue

import scala.collection.generic.CanBuildFrom
import scala.util.Try

trait Decoder[A] {
  self =>
  def apply(c: AttributeValue): A

  def apply(name: String, items: Map[String, AttributeValue]): A = {
    val vOpt = items.get(name)
    vOpt.fold(
      throw new Exception(s"Attribute '$name' not found in '$items'")
    )(
      v => apply(v)
    )
  }

  def map[B](f: A => B): Decoder[B] = new Decoder[B] {
    def apply(c: AttributeValue): B = f(self(c))
  }

  def flatMap[B](f: A => Decoder[B]): Decoder[B] = new Decoder[B] {
    def apply(c: AttributeValue): B = {
      f(self(c))(c)
    }
  }
}

object Decoder {
  def apply[A](implicit d: Decoder[A]): Decoder[A] = d

  def instance[A](f: AttributeValue => A): Decoder[A] = new Decoder[A] {
    def apply(c: AttributeValue): A = f(c)
  }

  /*
    GD:CR ->
    Change encode to decode

    Conversion from string to number (i.e. _.getN.toXXX) is not safe.
    Exception will be thrown when String is empty or null.
   */
  implicit val decodeAttributeValue: Decoder[AttributeValue] = instance(identity)
  implicit val decodeString: Decoder[String] = instance(_.getS)
  implicit val decodeBoolean: Decoder[Boolean] = instance(_.getBOOL)
  implicit val encodeFloat: Decoder[Float] = instance(_.getN.toFloat)
  implicit val encodeDouble: Decoder[Double] = instance(_.getN.toDouble)
  implicit val encodeByte: Decoder[Byte] = instance(_.getN.toByte)
  implicit val encodeShort: Decoder[Short] = instance(_.getN.toShort)
  implicit val encodeInt: Decoder[Int] = instance(_.getN.toInt)
  implicit val encodeLong: Decoder[Long] = instance(_.getN.toLong)
  implicit val encodeBigInt: Decoder[BigInt] = instance(a => BigDecimal(a.getN, MathContext.UNLIMITED).toBigInt())
  implicit val encodeBigDecimal: Decoder[BigDecimal] = instance(a => BigDecimal(a.getN, MathContext.UNLIMITED))
  implicit val encodeUUID: Decoder[UUID] = instance(a => UUID.fromString(a.getS))

  implicit def decodeCanBuildFrom[A, C[_]](implicit
                                           d: Decoder[A],
                                           cbf: CanBuildFrom[Nothing, A, C[A]]
                                          ): Decoder[C[A]] = instance { c =>
    import scala.collection.JavaConversions._

    val builder = cbf()
    /*
      GD:CR ->
      A bit shorter version. No sugar but still sweet I hope.
    */
    c.getL map {e => builder += d(e)}
    builder.result()
  }

  implicit def decodeOption[A](implicit d: Decoder[A]): Decoder[Option[A]] = new Decoder[Option[A]] {
    override def apply(c: AttributeValue): Option[A] = Try(d(c)).toOption
    override def apply(name: String, items: Map[String, AttributeValue]): Option[A] = {
      items.get(name).flatMap(apply)
    }
  }

  /**
    * @group Decoding
    */
  implicit def decodeMap[M[K, +V] <: Map[K, V], V](implicit
                                                   d: Decoder[V],
                                                   cbf: CanBuildFrom[Nothing, (String, V), M[String, V]]
                                                  ): Decoder[M[String, V]] = instance { c =>
    import scala.collection.JavaConversions._
    val builder = cbf()
    /*
      GD:CR ->
      Same thing as for decodeCanBuildFrom. It could be a candidate for a separate function.
    */
    c.getM map { case(k, v) => {
      builder += k -> d(v)
    }}
    builder.result()
  }

  implicit val monadDecode: Monad[Decoder] = new Monad[Decoder] {
    def pure[A](a: A): Decoder[A] = instance(_ => a)

    def flatMap[A, B](fa: Decoder[A])(f: A => Decoder[B]): Decoder[B] = fa.flatMap(f)
  }
}