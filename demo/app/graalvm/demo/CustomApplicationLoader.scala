package graalvm.demo

import play.api.{ApplicationLoader, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class CustomApplicationLoader extends GuiceApplicationLoader {
  override protected def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    initialBuilder.in(context.environment)
      .loadConfig(context.initialConfiguration)
      .overrides(overrides(context): _*)
  }
}