Sport Center
================
SportCenter is a POC reactive applications based on microservices architecture built on top of [Akka](akka.io) following a Event Sourcing based approach.

### Reactive application, Event Sourcing, Microservice architecture what and why shortly ###

Event Sourcing is about capturing sequence of event in journal. Each transaction/event is being recorded. State is recreated by replaying all the transactions/events.

### About the project ###
There are 3 type roles node in our akka cluster 

* Gateway:   Group of machines that links together 2 worlds using simple Load Balancer and Distributed Service Registry for internal cluster nodes. Every incoming request will be redirected for internal services if matched route is found. Each Gateway node provides following features:             
             Fault tolerant request routing layer using [Hystrix]( http://hystrix.github.com). To deliver fault tolerance Hystrix has built in the following features:
                * _Timeout for every request to an external system_             
                * _Limit of concurrent requests for external system_             
                * _Circuit breaker to avoid further requests_             
                * _Retry of a single request after circuit breaker has triggered_             
                * _Realtime aggregated dashboard for to retrieve runtime information on load_                                                                      
             _Distributed CRDT based Distributed Service Registry_ for domain using [akka-data-replication](https://github.com/patriknw/akka-data-replication). Every cluster node (exclude Gateways) before start register itself in Service Registry for being available for further requests             
             _Akka-Cluster for distributed cluster membership_                
  
* Crawlers: Cluster nodes to collect result from web. Akka-Cluster for distributed cluster membership. Deployed http route: http://{ip}:{port}/api/crawler
  
* Domain:  Loosely coupled command or query side microservice with sharded domain. We use Akka-Http, Akka-Persistense and Akka-Sharding to achieve this. Each Domain node is a place for one or several shards of the domain. Domain itself is a set of Persistent Actors.
           One Persistent Actor for one team. Every Game Persistent Actor persists incoming events in Event Journal(Mongo in owr case) and updates own state.
           Domain node by itself could be Query-side-results or Query-side-standing
              * Query-side-results node respond on results query like                  
                 http://{ip}:{port}/api/results/{dt} - returns all results for defined date              
                  http://{ip}:{port}/api/results/{team}/last - returns last default 5 results            
              * Query-side-standing node respond standing query 
                  http://{ip}:{port}/api/standings/{dt} - returns teams standing for defined date              
             We can run as many as we want Query-side-results and Query-side-standing node for scalability 
