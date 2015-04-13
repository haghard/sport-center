Sport Center
================
SportCenter is a POC reactive applications based on microservices architecture built on top of [Akka](akka.io) following a Event Sourcing based approach.

### Reactive application, Event Sourcing, Microservice architecture what and why shortly ###
Event Sourcing is about capturing sequence of event in journal. Each transaction/event is being recorded. State is recreated by replaying all the transactions/events.


### About the project ###
There are 3 type roles node in our akka cluster 
* Gateway:  Group of machines that links together 2 worlds using simple Load Balancer and Distributed Service Registry for internal cluster nodes. Every incoming request will be redirected for internal services if matched route is found. Each Gateway node provides following features:
`Fault tolerant request routing layer` using [Hystrix]( http://hystrix.github.com). To deliver fault tolerance Hystrix has built in the following features:
timeout for every request to an external system, limit of concurrent requests for external system, circuit breaker to avoid further requests, retry of a single request after circuit breaker has triggered, realtime aggregated dashboard for to retrieve runtime information on load 
`Distributed CRDT based Distributed Service Registry` for domain using [akka-data-replication](https://github.com/patriknw/akka-data-replication). Every cluster node (exclude Gateways) before start register itself in Service Registry for being available for further requests
`Akka-Cluster for distributed cluster membership`
  
* Crawlers:  Cluster nodes to collect result from web. Deployed http route: `http://{ip}:{port}/api/crawler`
`Akka-Cluster for distributed cluster membership`
  
* Domain:  Loosely coupled command or query side microservice with sharded domain. We use Akka-Http, Akka-Persistense and Akka-Sharding to achieve this. Each Domain node is a place for one or several shards of the domain. Domain itself is a set of Persistent Actors.
One Persistent Actor for one team. Every Game Persistent Actor persists incoming events in Event Journal (Mongo in own case) and updates own state.
Domain node by itself could be 2 kind `Query-side-results` or `Query-side-standing`:

* query-side-results node respond on results query like:
  _`http://{ip}:{port}/api/results/{dt}`_ - returns all results for defined date
  _`http://{ip}:{port}/api/results/{team}/last`_ - returns last default 5 results

* query-side-standing node respond standing query:
  _http://{ip}:{port}/api/standings/{dt}_ - returns teams standing for defined date

We can run as many as we want Query-side-results and Query-side-standing node for scalability 
