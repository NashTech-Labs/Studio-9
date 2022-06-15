package baile.services.process

import java.lang.reflect.{ Constructor, Member, Modifier }

import akka.actor.Props
import baile.utils.TryExtensions._

import scala.util.Try

object JobResultHandlerPropsFactory {

  def apply(handlerClassName: String, argumentSources: AnyRef*): Try[Props] = {

    def getConstructor(handlerClass: Class[_]): Try[Constructor[_]] = Try {
      handlerClass.getConstructors match {
        case Array(constructor) => constructor
        case _ => throw new RuntimeException("Only one constructor allowed")
      }
    }

    def loadParamValue(paramType: Class[_]): Try[Any] = Try {

      def getPossibleParamArguments(argumentSource: AnyRef): Seq[FoundArgument] = {
        val sourceClass = argumentSource.getClass
        val fieldValues = sourceClass
          .getFields
          .filter(field => paramType.isAssignableFrom(field.getType) && Modifier.isPublic(field.getModifiers))
          .map { field => FoundArgument(argumentSource, field, field.get(argumentSource)) }
        val methodValues = sourceClass
          .getMethods
          .filter {
            method => paramType.isAssignableFrom(method.getReturnType) &&
              method.getParameterCount == 0 &&
              Modifier.isPublic(method.getModifiers)
          }
          .map { method => FoundArgument(argumentSource, method, method.invoke(argumentSource)) }
        fieldValues ++ methodValues
      }

      val foundArguments = for {
        argumentSource <- argumentSources
        argument <- getPossibleParamArguments(argumentSource)
      } yield argument

      foundArguments match {
        case Seq(argument) =>
          argument.value
        case Seq() =>
          throw new RuntimeException(s"Not found argument for parameter of type $paramType")
        case arguments =>
          val argumentInfos = arguments.map { argument =>
            val sourceClass = argument.source.getClass.getCanonicalName
            val memberName = argument.member.getName
            val memberValue = argument.value
            s"Source class: $sourceClass. Source: ${ argument.source }. Member name: $memberName. Value: $memberValue"
          }
          throw new RuntimeException(
            s"Found multiple arguments for parameter of type $paramType: ${ argumentInfos.mkString("[", ", ", "]") }"
          )
      }
    }

    for {
      handlerClass <- Try(Class.forName(handlerClassName))
      constructor <- getConstructor(handlerClass)
      paramTypes <- Try(constructor.getParameterTypes)
      params <- Try.sequence(paramTypes.map(loadParamValue))
      props <- Try(Props(handlerClass, params: _*))
    } yield props

  }

  private case class FoundArgument(source: AnyRef, member: Member, value: AnyRef)

}
