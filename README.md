# lib-guillotine-client

Run [Guillotine](https://developer.enonic.com/docs/guillotine) GraphQL queries from the server-side JavaScript of an Enonic XP application, in-process.

The library looks up the Guillotine executor that `app-guillotine` registers in the OSGi service registry and calls it directly. There is no HTTP round-trip, no endpoint to configure, and no compile-time dependency on `app-guillotine`. The query runs in the caller's current XP context, so it is as fast and as permission-aware as a call to `lib-content`.

## Requirements

| Component | Version |
|-----------|---------|
| Enonic XP | 8.1.0 or later |
| app-guillotine | 9.0.0 or later, installed and started on the same XP instance |

`app-guillotine` 9 is the first release that exposes its executor as an OSGi service (`java.util.function.Function` with the property `function=guillotine.execute`). With an older Guillotine, or when Guillotine is not running, calls fail with an `IllegalStateException` (see [Errors](#errors)).

## Installation

Add the Enonic repository and include the library in the `build.gradle` of your XP application. Use the released version you want; `include` bundles the library into your application jar.

```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://repo.enonic.com/public'
    }
}

dependencies {
    include 'com.enonic.lib:lib-guillotine-client:1.0.0'
}
```

Rebuild and redeploy the application. No configuration is needed.

## Quick start

```js
const guillotineClient = require('/lib/guillotine-client');

const result = guillotineClient.query({
    query: `
        query ($key: ID!) {
            guillotine(project: "myproject", branch: "master") {
                get(key: $key) {
                    _id
                    displayName
                }
            }
        }
    `,
    variables: { key: '/my-site/posts/first-post' }
});

log.info('Content: %s', JSON.stringify(result.data.guillotine.get));
```

In TypeScript:

```ts
import { query } from '/lib/guillotine-client';
```

The return value is a standard GraphQL response object. On success it has `data`; when the query fails validation or a resolver fails, it has `errors`. GraphQL errors are returned, not thrown, so check `result.errors` before reading `result.data`.

## Example: a background task

Tasks, scheduled jobs and event listeners run without a request user. Without an explicit context the query sees only what an anonymous caller may read, so wrap the call in `contextLib.run` with the principals and branch you need.

`src/main/resources/tasks/count-draft-posts/count-draft-posts.yaml`:

```yaml
kind: "Task"
description: "Counts draft posts"
form: []
```

`src/main/resources/tasks/count-draft-posts/count-draft-posts.js`:

```js
const guillotineClient = require('/lib/guillotine-client');
const contextLib = require('/lib/xp/context');

const COUNT_QUERY = `
    {
        guillotine(project: "myproject", branch: "draft") {
            queryDsl(
                query: { term: { field: "type", value: { string: "com.example.myapp:post" } } }
                first: 100
            ) {
                _id
                displayName
            }
        }
    }
`;

exports.run = function () {
    const result = contextLib.run({
        repository: 'com.enonic.cms.myproject',
        branch: 'draft',
        principals: ['role:system.admin']
    }, function () {
        return guillotineClient.query({ query: COUNT_QUERY });
    });

    if (result.errors) {
        throw new Error('Guillotine query failed: ' + JSON.stringify(result.errors));
    }

    const posts = result.data.guillotine.queryDsl;
    log.info('Found %s draft posts', posts.length);
    posts.forEach(function (post) {
        log.info('  %s (%s)', post.displayName, post._id);
    });
};
```

## Example: an API that lists posts

An API endpoint that returns the children of a folder as JSON. APIs are the XP 8 way to expose HTTP endpoints from an application.

`src/main/resources/apis/posts/posts.yaml`:

```yaml
kind: "API"
title: "Posts API"
allow:
  - "role:system.everyone"
mount:
  - "web"
```

`allow` lists the principals that may call the endpoint, and `mount: ["web"]` exposes it on the web connector at `/api/<app.name>:posts`. The query still runs in the request context, so the caller sees only the content they are allowed to read.

`src/main/resources/apis/posts/posts.js`:

```js
const guillotineClient = require('/lib/guillotine-client');

const POSTS_QUERY = `
    query ($path: ID!, $first: Int!) {
        guillotine(project: "myproject", branch: "master") {
            getChildren(key: $path, first: $first, sort: "displayName ASC") {
                _id
                _path
                displayName
                ... on com_example_myapp_Post {
                    data {
                        title
                    }
                }
            }
        }
    }
`;

exports.GET = function (req) {
    return {
        body: guillotineClient.query({
            query: POSTS_QUERY,
            variables: {
                path: req.params.path || '/my-site/posts',
                first: parseInt(req.params.first, 10) || 10
            }
        })
    };
};
```

Call it with `GET /api/<app.name>:posts?path=/my-site/posts&first=5`. An object in `body` is serialized as JSON with status 200, so the client receives the GraphQL response as-is: `data` on success, `errors` on failure, the same shape as from Guillotine's own HTTP endpoint.

The inline fragment name follows your content type: Guillotine turns `com.example.myapp:post` into the GraphQL type `com_example_myapp_Post`, with the form fields under `data`. Use the Guillotine Playground to check the generated names for your own types.

## Context and permissions

The query runs in the XP context that is current when `query` is called.

- The `project` and `branch` arguments on `guillotine` select which content is queried.
- The context decides what the caller is allowed to read: content the current principals cannot see is left out, exactly as it would be for the same user over HTTP.
- In HTTP controllers (APIs, pages, parts, layouts, webapps) the request context, including the logged-in user, is used automatically.
- In tasks, scheduled jobs and event listeners, set the context yourself with `contextLib.run`, as shown above.

## API

### `query(params)`

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `params.query` | `string` | yes | The GraphQL query or mutation. |
| `params.variables` | `object` | no | Query variables, passed to Guillotine as-is. |

Returns a GraphQL-spec response object: `{ data: {...} }` on success, `{ errors: [...] }` on failure, or both when a resolver fails for part of the query.

### Errors

Thrown as JavaScript errors, not returned in `errors`:

| Situation | Error |
|-----------|-------|
| `params.query` missing | `Error: Parameter 'query' is required` |
| `app-guillotine` not installed, not started, or older than 9.0.0 | `IllegalStateException: Guillotine GraphQL executor API not found - is app-guillotine installed and started?` |

## Notes

- In-process queries bypass Guillotine's HTTP endpoints. Endpoint-level settings such as CORS and the `imageTransformations` restriction do not apply. In particular, image transformation arguments (`scale`, `quality`, `format` and others) are always allowed.
- Media URL fields return path components without a base, exactly as over HTTP. Prefix them with your public media base URL when building links.
- The service lookup happens on every call. The library picks the first `guillotine.execute` service whose bundle is active, so restarting `app-guillotine` does not require restarting your application.

## Building the library

Requires JDK 25.

```bash
./gradlew build                 # compiles, runs tests, produces build/libs/lib-guillotine-client-<version>.jar
./gradlew test                  # tests only
./gradlew publishToMavenLocal   # install locally for use from an app with mavenLocal() in its repositories
```

## License

[Apache License 2.0](LICENSE.txt)
