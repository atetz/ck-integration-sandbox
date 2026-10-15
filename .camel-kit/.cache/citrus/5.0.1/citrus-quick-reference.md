# Citrus YAML Quick Reference

Use this reference when generating Citrus YAML tests.

## Test Actions

| Action | Kind | Description |
|--------|------|-------------|
| `action` | testAction | Generic test action. |
| `agent` | testActionGroup | Agent service test actions. |
| `agent-connect` | testAction |  |
| `agent-run` | testAction |  |
| `ant` | testAction | Ant run test action. |
| `applyTemplate` | testAction | Apply template test action. |
| `camel` | testActionGroup | Test actions related to Apache Camel. |
| `camel-cli` | testActionGroup | Connect with Camel CLI to run commands. |
| `camel-cli-cmd` | testActionGroup | Camel CLI cmd operations. |
| `camel-cli-cmd-receive` | testAction | Receives a message from a Camel CLI integration. |
| `camel-cli-cmd-send` | testAction | Sends a message to Camel CLI integration. |
| `camel-cli-custom` | testAction | Runs a Camel integration with customized parameters. |
| `camel-cli-kubernetes` | testActionGroup | Camel Kubernetes plugin related operations. |
| `camel-cli-kubernetes-delete` | testAction | Deletes a Camel integration from Kubernetes. |
| `camel-cli-kubernetes-run` | testAction | Runs a Camel integration on Kubernetes. |
| `camel-cli-kubernetes-verify` | testAction | Verify the status and log messages for a Camel integratio... |
| `camel-cli-plugin` | testActionGroup | Manage Camel CLI plugins. |
| `camel-cli-plugin-add` | testAction | Adds a plugin to the Camel CLI installation. |
| `camel-cli-plugin-delete` | testAction | Deletes a plugin from the Camel CLI installation. |
| `camel-cli-run` | testAction | Runs a Camel integration. |
| `camel-cli-stop` | testAction | Stops a Camel integration. |
| `camel-cli-verify` | testAction | Verify the Camel integration status and log messages. |
| `camel-controlBus` | testAction | Connects with the Camel control bus to run operations. |
| `camel-createComponent` | testAction | Create components in the Camel registry. |
| `camel-createContext` | testAction | Create a new Camel context. |
| `camel-createRoutes` | testAction | Create a new Camel route in the context. |
| `camel-infra` | testActionGroup | Manage Camel infra services. |
| `camel-infra-run` | testAction | Runs a Camel infra service. |
| `camel-infra-stop` | testAction | Stops a Camel infra service. |
| `camel-jbang` | testActionGroup | Connect with Camel JBang to run commands. Deprecated, ple... |
| `camel-removeRoutes` | testAction | Remove given Camel routes. |
| `camel-startContext` | testAction | Starts the given Camel context. |
| `camel-startRoutes` | testAction | Start existing Camel routes in the context. |
| `camel-stopContext` | testAction | Stops the given Camel context. |
| `camel-stopRoutes` | testAction | Stop given Camel routes. |
| `camel-verifyRoute` | testAction | Verify a Camel route existence and status. |
| `camel-verifyRouteStats` | testAction | Verify Camel route statistics such as completed or failed... |
| `createEndpoint` | testAction | Create endpoints test action. |
| `createVariables` | testAction | Create variables test action. |
| `delay` | testAction | Delay test action. |
| `echo` | testAction | Echo test action. |
| `expectTimeout` | testAction | Expect timeout test action. |
| `fail` | testAction | Fail test action. |
| `groovy` | testAction | Groovy test action. |
| `http` | testActionGroup | Http related test actions. |
| `http-receiveRequest` | testAction | Receive an Http request as a server. |
| `http-receiveResponse` | testAction | Receive an Http response as a client. |
| `http-sendRequest` | testAction | Send a Http request as a client. |
| `http-sendResponse` | testAction | Send an Http response as a server. |
| `jbang` | testAction | JBang test actions. |
| `knative` | testActionGroup | Knative test actions. |
| `knative-createBroker` | testAction |  |
| `knative-createChannel` | testAction |  |
| `knative-createSubscription` | testAction |  |
| `knative-createTrigger` | testAction |  |
| `knative-deleteBroker` | testAction |  |
| `knative-deleteChannel` | testAction |  |
| `knative-deleteResource` | testAction |  |
| `knative-deleteSubscription` | testAction |  |
| `knative-deleteTrigger` | testAction |  |
| `knative-receiveEvent` | testAction |  |
| `knative-sendEvent` | testAction |  |
| `knative-verifyBroker` | testAction |  |
| `kubernetes` | testActionGroup | Kubernetes test actions. |
| `kubernetes-agent` | testAction |  |
| `kubernetes-connect` | testAction |  |
| `kubernetes-connectService` | testAction |  |
| `kubernetes-createAnnotations` | testAction |  |
| `kubernetes-createConfigMap` | testAction |  |
| `kubernetes-createCustomResource` | testAction |  |
| `kubernetes-createLabels` | testAction |  |
| `kubernetes-createResource` | testAction |  |
| `kubernetes-createSecret` | testAction |  |
| `kubernetes-createService` | testAction |  |
| `kubernetes-deleteConfigMap` | testAction |  |
| `kubernetes-deleteCustomResource` | testAction |  |
| `kubernetes-deleteResource` | testAction |  |
| `kubernetes-deleteSecret` | testAction |  |
| `kubernetes-deleteService` | testAction |  |
| `kubernetes-disconnect` | testAction |  |
| `kubernetes-disconnectService` | testAction |  |
| `kubernetes-verifyCustomResource` | testAction |  |
| `kubernetes-verifyPod` | testAction |  |
| `kubernetes-watchPodLogs` | testAction |  |
| `load` | testAction | Load properties test action. |
| `openapi` | testActionGroup | OpenAPI related test actions. |
| `openapi-receiveRequest` | testAction | Receives a client request as a server. |
| `openapi-receiveResponse` | testAction | Receives a response as a client. |
| `openapi-sendRequest` | testAction | Send a request as a client. |
| `openapi-sendResponse` | testAction | Sends a response as a server. |
| `plsql` | testAction | PLSQL test actions. |
| `print` | testAction | Print test action. |
| `purge` | testAction | Purge test action. |
| `purgeQueues` | testAction | Purge JMS queue test action. |
| `receive` | testAction | Receive message test action. |
| `selenium` | testActionGroup | Selenium test actions. |
| `selenium-alert` | testAction |  |
| `selenium-checkInput` | testAction |  |
| `selenium-clearCache` | testAction |  |
| `selenium-click` | testAction |  |
| `selenium-closeWindow` | testAction |  |
| `selenium-dropdownSelect` | testAction |  |
| `selenium-fillForm` | testAction |  |
| `selenium-find` | testAction |  |
| `selenium-focusWindow` | testAction |  |
| `selenium-getStoredFile` | testAction |  |
| `selenium-hover` | testAction |  |
| `selenium-javaScript` | testAction |  |
| `selenium-navigate` | testAction |  |
| `selenium-openWindow` | testAction |  |
| `selenium-page` | testAction |  |
| `selenium-screenshot` | testAction |  |
| `selenium-setInput` | testAction |  |
| `selenium-start` | testAction |  |
| `selenium-stop` | testAction |  |
| `selenium-storeFile` | testAction |  |
| `selenium-switchWindow` | testAction |  |
| `selenium-waitUntil` | testAction |  |
| `send` | testAction | Send message test action. |
| `sleep` | testAction | Sleep test action. |
| `soap` | testActionGroup | SOAP Web Services related test actions. |
| `soap-receiveRequest` | testAction | Receives a SOAP request as a server. |
| `soap-receiveResponse` | testAction | Receives a SOAP response as a client. |
| `soap-sendFault` | testAction | Sends a SOAP fault response as a server |
| `soap-sendRequest` | testAction | Sends a SOAP request as a client. |
| `soap-sendResponse` | testAction | Sends a SOAP response as a server. |
| `sql` | testAction | SQL test actions. |
| `start` | testAction | Start server test action. |
| `stop` | testAction | Stop server test action. |
| `stopTime` | testAction | Stop time test action. |
| `stopTimer` | testAction | Stop timer test action. |
| `testcontainers` | testActionGroup | Testcontainers test actions. |
| `testcontainers-compose` | testActionGroup |  |
| `testcontainers-compose-down` | testAction |  |
| `testcontainers-compose-up` | testAction |  |
| `testcontainers-start` | testActionGroup |  |
| `testcontainers-start-container` | testAction |  |
| `testcontainers-start-floci` | testAction |  |
| `testcontainers-start-kafka` | testAction |  |
| `testcontainers-start-localstack` | testAction |  |
| `testcontainers-start-mongodb` | testAction |  |
| `testcontainers-start-postgresql` | testAction |  |
| `testcontainers-start-redpanda` | testAction |  |
| `testcontainers-stop` | testAction |  |
| `trace` | testAction | Trace variables test action. |
| `transform` | testAction | Transform test action. |
| `waitFor` | testAction | Wait for test action. |

### Common Actions Detail

#### `send`

Send message test action.

| Property | Type | Description |
|----------|------|-------------|
| `actor` | string |  |
| `description` | string | Test action description printed when the action is executed. |
| `endpoint` | string | The message endpoint name or URI. Uses an endpoint URI or references an endpoint name. |
| `extract` | object | Extract message content to test variables before the message is sent. |
| `fork` | boolean | When set the send operation does not block while waiting for the response. |
| `message` | object | The message to send. |
| `process` | array | Process the message content when received. |
| `transform` | array | Transforms the message content before the message is validated. |

#### `receive`

Receive message test action.

| Property | Type | Description |
|----------|------|-------------|
| `actor` | string |  |
| `description` | string | Test action description printed when the action is executed. |
| `endpoint` | string | The message endpoint name or URI used to receive the message. Uses an endpoint URI or references an endpoint name. |
| `extract` | object | Extract message content to test variables after the message validation. |
| `headerValidator` | string | Explicit message header validator. |
| `headerValidators` | string | List of message header validators used to validate the message. |
| `ignore` | array | List of expressions to identify message elements that should be ignored during validation. |
| `message` | object | The expected message used to validate. |
| `namespace` | array | Optional XML namespace to validate. |
| `process` | array | Process the message content when received. |
| `select` | string | Message select expression to selectively receive a message. |
| `selector` | object | Message selector used to selectively receive a message. |
| `timeout` | integer | Receive timeout. |
| `transform` | array | Transforms the message content before the message is validated. |
| `validate` | array | Validation expressions evaluated on the message. |
| `validator` | string | Explicit message validator. |
| `validators` | string | List of message validators used to validate the message. |

#### `sql`

SQL test actions.

| Property | Type | Description |
|----------|------|-------------|
| `dataSource` | string |  |
| `description` | string | Test action description printed when the action is executed. |
| `extract` | array |  |
| `ignoreErrors` | boolean |  |
| `statements` | array |  |
| `transaction` | object |  |
| `validate` | array |  |

#### `sleep`

Sleep test action.

| Property | Type | Description |
|----------|------|-------------|
| `description` | string | Test action description printed when the action is executed. |
| `milliseconds` | string | Time to sleep in milliseconds. |
| `seconds` | string | Time to sleep in seconds. |

#### `echo`

Echo test action.

| Property | Type | Description |
|----------|------|-------------|
| `description` | string | Test action description printed when the action is executed. |
| `message` | string | The message to print. |

#### `camel`

Test actions related to Apache Camel.

| Property | Type | Description |
|----------|------|-------------|
| `actor` | string |  |
| `camelContext` | string | Name of the Camel context. |
| `description` | string | Test action description printed when the action is executed. |

#### `testcontainers`

Testcontainers test actions.

| Property | Type | Description |
|----------|------|-------------|
| `actor` | string |  |
| `description` | string | Test action description printed when the action is executed. |

## Endpoints

| Endpoint | Kind | Description |
|----------|------|-------------|
| `kubernetes-client` | testEndpoint |  |
| `context-messageStore` | testEndpoint |  |
| `kafka-synchronous` | testEndpoint |  |
| `jmx-server` | testEndpoint |  |
| `scp-server` | testEndpoint |  |
| `direct-synchronous` | testEndpoint |  |
| `docker-client` | testEndpoint |  |
| `kafka-asynchronous` | testEndpoint |  |
| `jmx-client` | testEndpoint |  |
| `rmi-client` | testEndpoint |  |
| `k8s-client` | testEndpoint |  |
| `mail-client` | testEndpoint |  |
| `ssh-client` | testEndpoint |  |
| `http-client` | testEndpoint |  |
| `websocket-client` | testEndpoint |  |
| `channel-asynchronous` | testEndpoint |  |
| `soap-server` | testEndpoint |  |
| `channel-synchronous` | testEndpoint |  |
| `sftp-server` | testEndpoint |  |
| `ftp-server` | testEndpoint |  |
| `camel-inOut` | testEndpoint |  |
| `vertx-asynchronous` | testEndpoint |  |
| `ftp-client` | testEndpoint |  |
| `camel-asynchronous` | testEndpoint |  |
| `rmi-server` | testEndpoint |  |
| `scp-client` | testEndpoint |  |
| `vertx-synchronous` | testEndpoint |  |
| `jms-synchronous` | testEndpoint |  |
| `camel-inOnly` | testEndpoint |  |
| `direct-asynchronous` | testEndpoint |  |
| `camel-synchronous` | testEndpoint |  |
| `soap-client` | testEndpoint |  |
| `jms-asynchronous` | testEndpoint |  |
| `sftp-client` | testEndpoint |  |
| `selenium-browser` | testEndpoint |  |
| `mail-server` | testEndpoint |  |
| `ssh-server` | testEndpoint |  |
| `http-server` | testEndpoint |  |
| `websocket-server` | testEndpoint |  |

## Testcontainers

| Container | Description |
|-----------|-------------|
| `assert` | Assert exception test action. |
| `async` | Async test action. |
| `catch` | Catch exception test action. |
| `conditional` | Conditional test action. |
| `doFinally` | Runs test actions after the test. |
| `iterate` | Iterate test action. |
| `parallel` | Parallel test action. |
| `repeat` | Repeat test action. |
| `repeatOnError` | Repeat on error test action. |
| `sequential` | Sequential test action. |
| `soap-assertFault` | Expects a SOAP fault response as a client. |
| `timer` | Timer test action. |

