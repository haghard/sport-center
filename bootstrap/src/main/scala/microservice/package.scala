import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder

package object microservice {

  def executor(poolName: String, n: Int) =
    Executors.newFixedThreadPool(n,
      new ThreadFactoryBuilder().setNameFormat(s"$poolName-%d").setDaemon(true).build())

}
