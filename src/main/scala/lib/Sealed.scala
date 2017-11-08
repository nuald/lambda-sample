package lib

import java.io._
import java.security.MessageDigest
import javax.crypto.{Cipher, CipherInputStream, CipherOutputStream, SealedObject}
import javax.crypto.spec.SecretKeySpec

import akka.event.LoggingAdapter
import lib.Common.using

import scala.util.Try

class Sealed[T <: Serializable](salt: String)(implicit logger: LoggingAdapter) {
  val Algorithm = "AES"
  val Transformation = "AES/ECB/PKCS5Padding"

  def getCipher(mode: Int): Cipher = {
    // Normalize salt to 16 bytes
    val msg = MessageDigest.getInstance("MD5")
    msg.update(salt.getBytes)
    // Create a key from the normalized salt
    val sks = new SecretKeySpec(msg.digest(), Algorithm)
    // Create a cipher with the key
    val cipher = Cipher.getInstance(Transformation)
    cipher.init(mode, sks)
    cipher
  }

  def toBytes(entry: T): Try[Array[Byte]] = {
    val cipher = getCipher(Cipher.ENCRYPT_MODE)
    val sealedObject = new SealedObject(entry, cipher)
    using(new ByteArrayOutputStream())(_.close) { ostream =>
      using(new ObjectOutputStream(
        new CipherOutputStream(ostream, cipher)
      ))(_.close) { outputStream =>
        outputStream.writeObject(sealedObject)
      }
      ostream.toByteArray
    }
  }

  def fromBytes(bytes: Array[Byte]): Try[T] = {
    val cipher = getCipher(Cipher.DECRYPT_MODE)
    using(new ObjectInputStream(
      new CipherInputStream(
        new ByteArrayInputStream(bytes), cipher
      )))(_.close) { inputStream =>
      val sealedObject = inputStream.readObject().asInstanceOf[SealedObject]
      sealedObject.getObject(cipher).asInstanceOf[T]
    }
  }
}
