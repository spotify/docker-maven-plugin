@Grab(group='com.spotify', module='pipeline-conventions', version='0.0.5-SNAPSHOT', changing=true)
import com.spotify.pipeline.*

use(Pipeline, build.Java, dist.Deb, deploy.Deploy) {
  pipeline {
    stage("Build") {
      version()
      compile()
      test()
      analyze()
      uploadArtifact()
    }
  }
}
