package microservice

import microservice.settings._
import microservice.api.BootableMicroservice

trait SystemSettings {
  self: BootableMicroservice ⇒

  def settings: CustomSettings = CustomSettings(system)
}

