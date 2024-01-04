pipeline {
  agent any
  tools { 
     jdk 'java21slave' 
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '1'))
  }
  parameters {
      booleanParam(defaultValue: false, description: 'Publish to DockerHub', name: 'PUBLISHIMAGE')
  }
  environment {
    LOGSTASH = 'nuc:5044'
  }  
  stages {
    stage('Git') {
      steps {
        git(url: 'https://github.com/wlanboy/MirrorService.git', branch: 'master')
      }
    }
    stage('Build') {
      steps {
        sh 'mvn clean package'
      }
    }
    stage('Container') {
      steps {
        sh 'docker build -t mirrorservice:latest . --build-arg JAR_FILE=./target/mirrorservice-0.2.1-SNAPSHOT.jar'
      }
    }
    stage('Publish') {
      when { expression { params.PUBLISHIMAGE == true } }
      steps {
        withDockerRegistry([ credentialsId: "dockerhub", url: "" ]) {
          sh 'docker push wlanboy/mirrorservice:latest'
        }
      }
    }
  }
}
