# Group-Feed 
Small project attempting to demonstrate akka ecosystem in simplified group posting

## Build and run


Run tests

```
sbt test multi-jvm:test
```

Run app

```
sbt run
```

## Sample usage

First new user needs to be registered
```
curl http://localhost:8080/api/user -H 'Content-Type: application/json' -d '{"id": 1, "name": "Bart"}'
```

Next group needs to be created (for simplicity auth token is just user id)
  
```
curl http://localhost:8080/api/group -H 'Content-Type: application/json' -H 'X-Token: 1' -d '{"id": 1}'
```

Joining group to be able to post to it and receive it's feed

```
curl http://localhost:8080/api/user/group -H 'Content-Type: application/json' -H 'X-Token: 1' -d '{"groupId": 1}'
```

Now user can post some message to group

```
curl http://localhost:8080/api/group/1/feed -H 'Content-Type: application/json' -H 'X-Token: 1' -d '{"user": {"id": 1, "name": "Bart"}, "content": "Hello World"}'
```

New message is now included in group feed

```
{"response":{"feed":[{"content":"Hello World","createdOn":1571212943474,"groupId":1,"id":"188be46b-9781-410c-a7e4-3ef13b4b22fd","user":{"id":1,"name":"Bart"}}],"id":1,"members":[1]}}
```

Let's verify if message was propagated to group members feed

```
curl http://localhost:8080/api/user/feed -H 'X-Token: 1'
```

## Further work/possible improvements

- delivery guarantee in distributed pub sub is at most once (messages can be lost). To achieve at least once Kafka might be used instead
- replace Java serialization of messages
- use akka streams and leverage backpressure for fetching feeds 
