Sport Center
================
SportCenter is a POC reactive applications based on microservices architecture built on top of [Akka](akka.io) used Distributed Domain Driven Design approach.


### Reactive application with Microservice architecture and Distributed Domain Driven Design what and why shortly ###
Event Sourcing is about capturing sequence of event in journal. Each transaction/event is being recorded. State is recreated by replaying all the transactions/events.

Add more....

### DataStore

This application requires a distributed journal. Storage backends for journals and snapshot stores are pluggable in Akka persistence. In this case we are using Cassandra.
You can find other journal plugins [here](http://akka.io/community/?_ga=1.264939791.1443869017.1408561680).
The journal is specified in **application.conf**

### About the project ###

This application uses a simple domain to demonstrate CQRS and event sourcing with Akka Persistence. This domain is a nba games: results and standings

There are 3 type roles node in our akka cluster(Gateway, Crawler(Write side), Http Microservice/Domain(Read size))
For better design we should have splitted domain and http layer(future work) 

### Gateway   
Group of machines that links together two worlds using simple Load Balancer and Distributed Service Registry for internal cluster nodes. Every incoming request will be redirected for internal services if matched route is found. Each Gateway node provides following features:

`Fault tolerant request routing layer` using [Hystrix]( http://hystrix.github.com). To deliver fault tolerance Hystrix has built in the following features:
timeout for every request to an external system, limit of concurrent requests for external system, circuit breaker to avoid further requests, retry of a single request after circuit breaker has triggered, realtime aggregated dashboard for to retrieve runtime information on load 

`Distributed CRDT based Service Registry` for domain using [akka-distributed-data]. Every cluster node (exclude Gateways) before start register itself in Service Registry for being available for further requests

`Gateway-turbine` aggregate data from Gateway nodes into a single stream of metrics, which in turn streams the aggregated data to the browser for display in the UI.

`Akka-Cluster` for distributed cluster membership

Fault tolerance aspect: Gateway process guarantees progress with lose up to n-1 `Gateway` node

### Crawler 

Cluster nodes to collect result from web. We use `RoundRobinPool` to scale crawler process to multiple machine and `Akka-Cluster` for distributed cluster membership. This processes deploy http route: `http://{ip}:{port}/api/crawler`
Fault tolerance aspect: Crawling process guarantees progress with losing up to n-1 `Crawler` node 
  
### Http Microservice Node/Domain  
Loosely coupled command or query side microservice with sharded domain. We use Akka-Http, Akka-Persistense and Akka-Sharding to achieve this. Each Domain node is a place for one or several shards of the domain. Domain itself is a set of Persistent Actors.
One Persistent Actor for one team. Every Game Persistent Actor persists incoming events in Event Journal (Cassandra in own case) and updates own state.
`Http Microservice Node/Domain` node by itself could be 2 kinds **query-side-results** with routes [`http://{ip}:{port}/api/results/{dt}` and `http://{ip}:{port}/api/results/{team}/last`] and **query-side-standing** `http://{ip}:{port}/api/standings/{dt}`. They are both processes that can serve read queries. If gateway layer ran we can start and stop as many as we want **query-side-results** and **query-side-standing** processes to increase read throughput. We assume that our materialized views is so small that each machine can hold a copy of it in memory. This allows query side to be completely based on memory, and don't perform any request to the underlying db. We use `PersistentView` concept that acts like a streamer for persisted events.
Fault tolerance aspect: We can stay responsive for reads with losing up to n-1 one of every type `Query-side-nnn` node 


### Flow
Add more....


How to run with docker
---------------------------
All docker's configuration can be found in `sportcenter/bootstrap/build.sbt`. You can build docker images by itself using `sbt bootstrap/*:docker`

##### Cassandra cluster #####

Run [Cassandra](http://http://cassandra.apache.org) cluster with at least 3 nodes. Example for 192.168.0.134 192.168.0.82, 192.168.0.88 

> docker run -d -e CASSANDRA_BROADCAST_ADDRESS=192.168.0.182 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.148 -e CASSANDRA_CLUSTER_NAME="haghard_cluster" -e CASSANDRA_HOME="/var/lib/cassandra" -e CASSANDRA_START_RPC="true" -e CASSANDRA_RACK="wr0" -e CASSANDRA_DC="west" -e CASSANDRA_ENDPOINT_SNITCH="GossipingPropertyFileSnitch" -p 7000:7000 -p 7001:7001 -p 9042:9042 -p 9160:9160 -p 7199:7199 -v /home/haghard/Projects/cassandra-db-3.7:/var/lib/cassandra cassandra:3.7
     
> docker run -d -e CASSANDRA_BROADCAST_ADDRESS=192.168.0.38 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.148 -e CASSANDRA_CLUSTER_NAME="haghard_cluster" -e CASSANDRA_HOME="/var/lib/cassandra" -e CASSANDRA_START_RPC="true" -e CASSANDRA_RACK="wr1" -e CASSANDRA_DC="west" -e CASSANDRA_ENDPOINT_SNITCH="GossipingPropertyFileSnitch" -p 7000:7000 -p 7001:7001 -p 9042:9042 -p 9160:9160 -p 7199:7199 -v /home/haghard/Projects/cassandra-db-3.7:/var/lib/cassandra cassandra:3.7
   
> docker run -d -e CASSANDRA_BROADCAST_ADDRESS=192.168.0.148 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.148 -e CASSANDRA_CLUSTER_NAME="haghard_cluster" -e CASSANDRA_HOME="/var/lib/cassandra" -e CASSANDRA_START_RPC="true" -e CASSANDRA_RACK="er0" -e CASSANDRA_DC="east" -e CASSANDRA_ENDPOINT_SNITCH="GossipingPropertyFileSnitch" -p 7000:7000 -p 9042:9042 -p 9160:9160 -p 7199:7199 -v /home/haghard/Projects/cassandra-db-3.7:/var/lib/cassandra cassandra:3.7
  
> docker run -d -e CASSANDRA_BROADCAST_ADDRESS=192.168.0.57 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.148 -e CASSANDRA_CLUSTER_NAME="haghard_cluster" -e CASSANDRA_HOME="/var/lib/cassandra" -e CASSANDRA_START_RPC="true" -e CASSANDRA_RACK="er1" -e CASSANDRA_DC="east" -e CASSANDRA_ENDPOINT_SNITCH="GossipingPropertyFileSnitch" -p 7000:7000 -p 9042:9042 -p 9160:9160 -p 7199:7199 -v /home/haghard/Projects/cassandra-db-3.7:/var/lib/cassandra cassandra:3.7


where /home/haghard/Projects/cassandra_docker has this subdirectories:

commitlog

data

saved_caches

It's important to allow cassandra save data on local disk and restore it when docker image starts. If you don't care about saving data between cassandra runs you can drop it.  

##### Local cluster run #####

We can run cluster with 6 nodes locally using sbt like this

sbt lgateway0
sbt lgateway1
sbt lgateway2

sbt lcrawler0

sbt lresults0

sbt lstanding0

This commands will run single instance for each cluster node(Gateway, Crawler, HttpResults, HttpStandings). All command aliases you can find in sportcenter/bootstrap/build.sbt. And now you can check it `http GET <local-ip>:2561/routes`


##### Sport-center cluster with docker on multiple machine #####

Docker image id can be discovered with `docker images` command. Let's suppose we starting 3 gateway/seed node on 109.234.39.32, 109.234.39.76, 109.234.39.77  


##### Run gateway layer #####
Important convention: if AKKA_PORT=x then HTTP_PORT should be AKKA_PORT + 10 because TurbineServer relies on that

host 192.168.0.182
docker run --net="host" -d -p 2555:2555 -p 2565:2565 haghard/sport-center-gateway-microservice:v0.4 --HOST=192.168.0.182 --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555

host 192.168.0.38
docker run --net="host" -it -p 2555:2555 -p 2565:2565 haghard/sport-center-gateway-microservice:v0.4 --HOST=192.168.0.38 --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555

host 192.168.0.148
docker run --net="host" -d -p 2555:2555 -p 2565:2565 haghard/sport-center-gateway-microservice:v0.4 --HOST=192.168.0.148 --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555

Now, we have 3 http endpoints for underlaying api 192.168.0.182:2565, 192.168.0.38:2565, 192.168.0.148:2565 which are seed nodes for the whole akka cluster.


##### Run crawler layer #####

host 192.168.0.57
docker run --net="host" -it -p 2585:2585 -p 2586:2586 haghard/sport-center-crawler-microservice:v0.4 --HOST=192.168.0.57 --AKKA_PORT=2585 --HTTP_PORT=2586 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555 --DB_HOSTS=192.168.0.182

...


##### Run query side http layer #####

host 192.168.0.182 
docker run  --net="host" -it -p 2571:2571 -p 2568:2568 haghard/sport-center-query-side-results:v0.4 --HOST=192.168.0.182 --AKKA_PORT=2571 --HTTP_PORT=2568 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555 --DB_HOSTS=192.168.0.182


....

host 192.168.0.148
docker run --net="host" -d -p 2571:2571 -p 2568:2568 haghard/sport-center-query-side-standings:v0.4 --HOST=192.168.0.148 --AKKA_PORT=2571 --HTTP_PORT=2568 --SEED_NODES=192.168.0.182:2555,192.168.0.38:2555,192.168.0.148:2555 --DB_HOSTS=192.168.0.182

....


##### Hystrix-dashboard #####
 
To access [hystrix-dashboard](https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard) and attach streams of metrics from Gateway nodes:
                         
> git clone https://github.com/Netflix/Hystrix.git

> cd Hystrix/hystrix-dashboard

> ../gradlew jettyRun
  
> Running at http://localhost:7979/hystrix-dashboard
  
Once dashboard running, you can open http://localhost:7979/hystrix-dashboard
To connect hystrix-dashboard to `Gateway-turbine` use http://192.168.0.62:6500/turbine.stream in hystrix-dashboard UI. 


For testing we can use this:

### Command line HTTP client ###
  [Httpie](http://httpie.org/)

  `http GET 192.168.0.182:2565/api/login?"user=lector&password=lector@gmail.com"`
  
  `http GET 192.168.0.182:2565/routes`
  
  `http GET 192.168.0.182:2565/discovery/scalar`
  
  `http GET 192.168.0.182:2565/discovery/stream`
  
  `http GET http://192.168.0.182:2568/showShardRegions`
  
  `http GET 192.168.0.38:2565/api/results/2014-01-29 Authorization:...`
  
  `http GET 192.168.0.38:2565/api/results/2014-01-29 Authorization:...`
  
  `http GET 192.168.0.38:2565/api/results/okc/last Authorization:...`
  
  `http GET 192.168.0.38:2565/api/standings/2013-01-28 Authorization:=...`

### Apache benchmark example ###
  
  `ab -n 50 -c 4 -t 30 -H "Authorization:..." 192.168.0.38:2561/api/results/hou/last`

