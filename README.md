Sport Center
================
SportCenter is a POC reactive applications based on microservices architecture built on top of [Akka](akka.io) following a Event Sourcing based approach.

### Reactive application, Event Sourcing, Microservice architecture what and why shortly ###
Event Sourcing is about capturing sequence of event in journal. Each transaction/event is being recorded. State is recreated by replaying all the transactions/events.

Add more....

### DataStore

This application requires a distributed journal. Storage backends for journals and snapshot stores are pluggable in Akka persistence. In this case we are using Cassandra.
You can find other journal plugins [here](http://akka.io/community/?_ga=1.264939791.1443869017.1408561680).
The journal is specified in **application.conf**

### About the project ###

This application uses a simple domain to demonstrate CQRS and event sourcing with Akka Persistence. This domain is a nba games: results and standings

There are 3 type roles node in our akka cluster(Gateway, Crawler, Http Microservice Node/Domain) 

### Gateway   
Group of machines that links together two worlds using simple Load Balancer and Distributed Service Registry for internal cluster nodes. Every incoming request will be redirected for internal services if matched route is found. Each Gateway node provides following features:

`Fault tolerant request routing layer` using [Hystrix]( http://hystrix.github.com). To deliver fault tolerance Hystrix has built in the following features:
timeout for every request to an external system, limit of concurrent requests for external system, circuit breaker to avoid further requests, retry of a single request after circuit breaker has triggered, realtime aggregated dashboard for to retrieve runtime information on load 

`Distributed CRDT based Service Registry` for domain using [akka-data-replication](https://github.com/patriknw/akka-data-replication). Every cluster node (exclude Gateways) before start register itself in Service Registry for being available for further requests

`Gateway-turbine` aggregate data from Gateway nodes into a single stream of metrics, which in turn streams the aggregated data to the browser for display in the UI.

`Akka-Cluster` for distributed cluster membership

Fault tolerance aspect: Gateway process guarantees progress with lose up to n-1 `Gateway` node

### Crawler 

Cluster nodes to collect result from web. We use `RoundRobinPool` to scale crawler process to multiple machine and `Akka-Cluster` for distributed cluster membership. This processes deploy http route: `http://{ip}:{port}/api/crawler`
Fault tolerance aspect: Crawling process guarantees progress with lose up to n-1 `Crawler` node 
  
### Http Microservice Node/Domain  
Loosely coupled command or query side microservice with sharded domain. We use Akka-Http, Akka-Persistense and Akka-Sharding to achieve this. Each Domain node is a place for one or several shards of the domain. Domain itself is a set of Persistent Actors.
One Persistent Actor for one team. Every Game Persistent Actor persists incoming events in Event Journal (Cassandra in own case) and updates own state.
`Http Microservice Node/Domain` node by itself could be 2 kinds **query-side-results** with routes [`http://{ip}:{port}/api/results/{dt}` and `http://{ip}:{port}/api/results/{team}/last`] and **query-side-standing** `http://{ip}:{port}/api/standings/{dt}`. They are both processes that can serve read queries. If gateway layer ran we can start and stop as many as we want **query-side-results** and **query-side-standing** processes to increase read throughput. We assume that our materialized views is so small that each machine can hold a copy of it in memory. This allows query side to be completely based on memory, and don't perform any request to the underlying db. We use `PersistentView` concept that acts like a streamer for persisted events.
Fault tolerance aspect: We can stay responsive for reads with lost up to n-1 one of every type `Query-side-nnn` node 


### Flow
Add more....


How to run with docker
---------------------------
You should checkout on branch cassandra. All 4 docker image configuration can be found in `sportcenter/bootstrap/build.sbt`. You can build docker images by itself using `sbt bootstrap/*:docker` for each commented images

##### Cassandra #####

Run [Cassandra](http://http://cassandra.apache.org) cluster with at least 3 node. Example for 192.168.0.182, 192.168.0.134, 192.168.0.218 

> docker run --net="host" -it -e HOST=192.168.0.182 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=-9223372036854775808 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

> docker run --net="host" -it -e HOST=192.168.0.134 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=2317766532957582345 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

> docker run --net="host" -it -e HOST=192.168.0.218 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=3558217197862924070 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

where /home/haghard/Projects/cassandra_docker ls -la

``bash
  haghard 4096 May 11 05:55 commitlog
  haghard 4096 May 11 05:23 data
  haghard 4096 May 10 10:06 saved_caches
``

##### Cluster run with docker example #####

Docker image id can be discovered with `docker images`. Let's suppose we starting 3 gateway/seed node on 192.168.0.1, 192.168.0.2, 192.168.0.3  

##### Run gateway layer #####

docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565

docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555

docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555,192.168.0.3:2555

Now we have 3 http endpoints for underlaying api 192.168.0.1:2565, 192.168.0.2:2565, 192.168.0.3:2565 

##### Run crawler layer #####

docker run --net="host" -it `crawler-docker-image-id` --AKKA_PORT=2556 --HTTP_PORT=2567 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --DB_HOSTS=192.168.0.182,192.168.0.134

docker run --net="host" -it `crawler-docker-image-id` --AKKA_PORT=2557 --HTTP_PORT=2568 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --DB_HOSTS=192.168.0.182,192.168.0.134

...


##### Run query side http layer #####

docker run --net="host" -it `sport-center-query-side-results-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --DB_HOSTS=192.168.0.182,192.168.0.134

....

docker run --net="host" -it `sport-center-query-side-standings-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --DB_HOSTS=192.168.0.182,192.168.0.134

....


##### Hystrix-dashboard #####
 
To access [hystrix-dashboard](https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard) and attach streams of metrics from Gateway nodes:

> git clone https://github.com/Netflix/Hystrix.git

> cd Hystrix/hystrix-dashboard

> ../gradlew jettyRun
  
> Running at http://localhost:7979/hystrix-dashboard
  
Once dashboard running, you can open http://localhost:7979/hystrix-dashboard
To connect hystrix-dashboard to `Gateway-turbine` use http://{ip}:6500/turbine.stream in hystrix-dashboard UI. 


For testing we can use this:

### Command line HTTP client ###
  [Httpie](http://httpie.org/)

  `http GET 192.168.0.1:2565/routes`,
  
  `http GET 192.168.0.1:2565/api/results/2014-01-29`,
  
  `http GET 192.168.0.1:2565/api/results/okc/last`,
  
  `http GET 192.168.0.1:2565/api/standings/2013-01-28`