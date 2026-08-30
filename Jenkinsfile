pipeline {
    agent any
    tools {
        maven 'maven-3.9'
        jdk 'jdk-17'
    }
    options {
        timestamps()
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests package'
            }
        }
        stage('Docker image') {
            steps {
                sh 'docker build -t demo-api:local demo-app'
            }
        }
        stage('Tests') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                    sh 'mvn -B -pl demo-tests -am test'
                }
            }
        }
        stage('Allure') {
            steps {
                allure includeProperties: false, jdk: '', results: [[path: 'demo-tests/target/allure-results']]
            }
        }
    }
}
