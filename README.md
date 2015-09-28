# Karaf Microservices - Tooling

This projects brings tools for easy deployment of Apache-Karaf Server used in the Karaf-Microservice samples. It consists of the following modules: 

* karaf-deployer-maven-plugin: 
  a maven plugin for easy deployment of an artifact on a Apache Karaf server via REST call. 
* Karaf-Service-Runtime:
  a custom Apache Karaf Runtime ready to use for the Karaf-Microservices samples. 
* Karaf-Service-Docker: 
  taken the Karaf-Service-Runtime wraped as Docker Image build on top of alpine based minimal linux and JDK 8. 
  
## Karaf - Microservice Docker

The Karaf-Service-Docker module contains a ready to use Apache Karaf server as basis for the Microservices samples. To start this Docker image just issue:

	mvn docker:start  
	
to start the docker image. 

To stop and clean the previous started docker image issue the following comand: 

	mvn docker:stop