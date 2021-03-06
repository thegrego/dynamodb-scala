package com.onzo.dynamodb

import java.util.UUID
import cats.functor.Contravariant
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import scala.collection.generic.IsTraversableOnce

trait Encoder[A] {
  //self =>
  def apply(a: A): AttributeValue

  def apply(name: String, a: A): Map[String, AttributeValue] = {
    Map(name -> apply(a))
  }

  def contramap[B](f: B => A): Encoder[B] = Encoder.instance(b => apply(f(b)))
}

object Encoder {
  def apply[A](implicit e: Encoder[A]): Encoder[A] = e

  def instance[A](f: A => AttributeValue): Encoder[A] = new Encoder[A] {
    def apply(a: A): AttributeValue = f(a)
  }

  implicit def encodeTraversableOnce[A0, C[_]](implicit
                                               e: Encoder[A0],
                                               is: IsTraversableOnce[C[A0]] {type A = A0}
                                              ): Encoder[C[A0]] =
    instance { list =>
      val items = new java.util.ArrayList[AttributeValue]()

      is.conversion(list).foreach { a =>
        items add e(a)
      }

      new AttributeValue().withL(items)
    }

  implicit val encodeAttributeValue: Encoder[AttributeValue] = instance(identity)
  implicit val encodeString: Encoder[String] = instance(new AttributeValue().withS(_))
  implicit val encodeBoolean: Encoder[Boolean] = instance(new AttributeValue().withBOOL(_))
  implicit val encodeFloat: Encoder[Float] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeDouble: Encoder[Double] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeByte: Encoder[Byte] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeShort: Encoder[Short] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeInt: Encoder[Int] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeLong: Encoder[Long] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeBigInt: Encoder[BigInt] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeBigDecimal: Encoder[BigDecimal] = instance(a => new AttributeValue().withN(a.toString))
  implicit val encodeUUID: Encoder[UUID] = instance(uuid => new AttributeValue().withS(uuid.toString))

  implicit def encodeOption[A](implicit e: Encoder[A]): Encoder[Option[A]] = new Encoder[Option[A]] {
    //self =>
    /*
      GD:CR ->
      Invoking get on an Option can cause Runtime error.
      It's safer to use map {} getOrElse in case the Option payload must be processed.
     */
    override def apply(a: Option[A]): AttributeValue = a.map {e(_)} getOrElse(new AttributeValue().withNULL(true))

    override def apply(name: String, a: Option[A]): Map[String, AttributeValue] = {
      /*
        GD:CR ->
        One implementation of 'fold' on an Option is like "if empty doSth else processData"
       */
      a.fold {Map.empty[String,AttributeValue]} {v => Map(name -> e(v))}
    }
  }


  implicit def encodeMapLike[M[K, +V] <: Map[K, V], V](implicit
                                                       e: Encoder[V]
                                                      ): Encoder[M[String, V]] = Encoder.instance { m =>
    /*
      GD:CR ->
      Using JavaConverters is another option to process Java collections.
     */
    import scala.collection.JavaConverters._

    new AttributeValue().withM(m.mapValues(e(_)).asJava)
  }

  def encodeEither[A, B](leftKey: String, rightKey: String)(implicit
                                                            ea: Encoder[A],
                                                            eb: Encoder[B]
  ): Encoder[Either[A, B]] = instance { a =>
    val map = new java.util.HashMap[String, AttributeValue]()
    a.fold(
      a => map.put(leftKey, ea(a)),
      b => map.put(rightKey, eb(b)))
    new AttributeValue().withM(map)
  }

  implicit val contravariantEncode: Contravariant[Encoder] = new Contravariant[Encoder] {
    def contramap[A, B](e: Encoder[A])(f: B => A): Encoder[B] = e.contramap(f)
  }

}
