package graalvm.demo

import javax.inject.{Inject, Singleton}
import play.api.Environment
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter

@Singleton
class Filters @Inject()(env: Environment, corsFilter: CORSFilter) extends HttpFilters {

  override val filters = {
    // Use the example filter if we're running development mode. If
    // we're running in production or test mode then don't use any
    // filters at all.
    //Seq(loginFilter, corsFilter, loggingFilter)
     Seq.empty[EssentialFilter]
  }
}