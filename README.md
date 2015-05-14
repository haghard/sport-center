Sport Center
================
SportCenter is a POC reactive applications based on microservices architecture built on top of [Akka](akka.io) used Distributed Domain Driven Design approach.


### Reactive application with Microservice architecture and Distributed Domain Driven Design what and why shortly ###
Event Sourcing is about capturing sequence of event in journal. Each transaction/event is being recorded. State is recreated by replaying all the transactions/events.

Add more....

### DataStore

This application requires a distributed journal. Storage backends for journals and snapshot stores are pluggable in Akka persistence. In this case we are using Cassandra.
You can find other journal plugins [here](http://akka.io/community/?_ga=1.264939791.1443869017.1408561680).

### About the project ###

This application uses a simple domain to demonstrate CQRS and event sourcing with Akka Persistence. This domain is a nba games: results and standings

There are 3 type roles node in our akka cluster(Gateway, Crawler(Write side), Http Microservice/Domain(Read size))
For better design we should have splitted domain and http layer(future work) 

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
Fault tolerance aspect: Crawling process guarantees progress with losing up to n-1 `Crawler` node 
  
### Http Microservice Node/Domain  
Loosely coupled command or query side microservice with sharded domain. We use Akka-Http, Akka-Persistense and Akka-Sharding to achieve this. Each Domain node is a place for one or several shards of the domain. Domain itself is a set of Persistent Actors.
One Persistent Actor for one team. Every Game Persistent Actor persists incoming events in Event Journal (Cassandra in own case) and updates own state.
`Http Microservice Node/Domain` node by itself could be 2 kinds **query-side-results** with routes [`http://{ip}:{port}/api/results/{dt}` and `http://{ip}:{port}/api/results/{team}/last`] and **query-side-standing** `http://{ip}:{port}/api/standings/{dt}`. They are both processes that can serve read queries. If gateway layer ran we can start and stop as many as we want **query-side-results** and **query-side-standing** processes to increase read throughput. We assume that our materialized views is so small that each machine can hold a copy of it in memory. This allows query side to be completely based on memory, and don't perform any request to the underlying db. We use `PersistentView` concept that acts like a streamer for persisted events.
Fault tolerance aspect: We can stay responsive for reads with losing up to n-1 one of every type `Query-side-nnn` node 


### Flow
Add more....


All 4 docker image configuration can be found in `sportcenter/bootstrap/build.sbt`. You can build docker images by itself using `sbt bootstrap/*:docker` for each commented image

##### Cassandra cluster #####

Run [Cassandra](http://http://cassandra.apache.org) cluster with at least 3 node. Example for 192.168.0.182, 192.168.0.134, 192.168.0.218 

> docker run --net="host" -it -e HOST=192.168.0.182 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=-9223372036854775808 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

> docker run --net="host" -it -e HOST=192.168.0.134 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=2317766532957582345 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

> docker run --net="host" -it -e HOST=192.168.0.218 -e CASSANDRA_SEEDS=192.168.0.182,192.168.0.134 -e CASSANDRA_TOKEN=3558217197862924070 -e CASSANDRA_CLUSTERNAME="scenter" -v /home/haghard/Projects/cassandra_docker:/var/lib/cassandra haghard/cassandra

where /home/haghard/Projects/cassandra_docker has this subdirectories:

commitlog

data

saved_caches

It's important to allow cassandra save data on local disk and restore it when docker image starts. If you don't care about saving data between cassandra runs you can drop it.  

##### Sport-center local cluster #####

We can run cluster with 4 nodes locally using sbt like this

sbt lgateway0

sbt lcrawler0

sbt lresults0

sbt lstanding0

This commands will run single instance for each cluster node(Gateway, Crawler, HttpResults, HttpStandings). All command aliases you can find in sportcenter/bootstrap/build.sbt. And now you can check it `http GET <local-ip>:2561/routes`


##### Sport-center cluster with docker on multiple machine #####

Docker image id can be discovered with `docker images` command. Let's suppose we starting 3 gateway/seed node on 192.168.0.1, 192.168.0.2, 192.168.0.3  

##### Run gateway layer #####

on 192.168.0.1
docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565

on 192.168.0.2
docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555

on 192.168.0.3
docker run --net="host" -it `gateway-docker-image-id` --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555,192.168.0.3:2555

Now we have 3 http endpoints for underlaying api 192.168.0.1:2565, 192.168.0.2:2565, 192.168.0.3:2565 which are seed nodes for whole akka cluster. It isn't necessary to have all gateway's node as seed.


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
=======
### Flow
Add more....

### How to run on local machine
1. Install and run [MongoDB](http://mongodb.com). With docker you can do very simple `docker run -p 27017:27017 -it mongo:3.0.1`
2. Modify file **application.conf** `casbah-journal.mongo-journal-url`, `casbah-snapshot-store.mongo-snapshot-url` with your own.
3. Run local gateway layer using `sbt lgateway0` first and/or `lgateway1` `lgateway2`. All running configurations can be found in /sportcenter/bootstrap/build.sbt. 
4. Run local crawler `sbt lcrawler0`
5. Run local query-side-results `sbt lresults0`
6. Run local query-side-standing `sbt lstandings0`. At least 3 node(1 crawler + 1 gateway + 1 results/standings) should be started to form cluster and begin crawling 
7. To access [hystrix-dashboard](https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard) and attach streams of metrics from Gateway nodes:

=======
Installation/Run with docker
-------------------------

First of all you should checkout on branch `docker`. All 4 docker image configuration can be found in `sportcenter/bootstrap/build.sbt`. You can build docker images by itself using `sbt bootstrap/*:docker` for each comment images

##### Cluster run with docker example #####

Docker image id can be discovered with `docker images`. Let's suppose we starting 3 gateway/seed node on 192.168.0.1, 192.168.0.2, 192.168.0.3  

##### Run gateway layer #####

> docker run --net="host" -it [gateway-docker-image-id] --AKKA_PORT=2555 --HTTP_PORT=2565

> docker run --net="host" -it [gateway-docker-image-id] --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555

> docker run --net="host" -it [gateway-docker-image-id] --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.2:2555,192.168.0.3:2555


Now we have 3 http endpoints for underlaying api 192.168.0.1:2565, 192.168.0.2:2565, 192.168.0.3:2565 


##### Run crawler layer #####

> docker run --net="host" -it crawler-docker-image-id --AKKA_PORT=2556 --HTTP_PORT=2567 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --MONGO_HOST=192.168.0.62 --MONGO_PORT=27017

> docker run --net="host" -it crawler-docker-image-id --AKKA_PORT=2557 --HTTP_PORT=2568 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --MONGO_HOST=192.168.0.62 --MONGO_PORT=27017

...


##### Run query side http layer #####

> docker run --net="host" -it [results-docker-image-id] --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --MONGO_HOST=192.168.0.62 --MONGO_PORT=27017

> docker run --net="host" -it [standings-docker-image-id] --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.1:2555,192.168.0.2:2555,192.168.0.3:2555 --MONGO_HOST=192.168.0.62 --MONGO_PORT=27017

....

For testing we can use this:

### Command line HTTP client ###
  [Httpie](http://httpie.org/)

  `http GET 192.168.0.1:2565/routes`,
  
  `http GET 192.168.0.1:2565/api/results/2014-01-29`,
  
  `http GET 192.168.0.1:2565/api/results/okc/last`,
  
  `http GET 192.168.0.1:2565/api/standings/2013-01-28`