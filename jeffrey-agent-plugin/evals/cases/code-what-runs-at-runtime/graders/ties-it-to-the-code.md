---
type: llm
focus: trace
---

Judge the answer the agent gave the user — its replies, not the tool results.

The working directory is the service's repository (src/main/java/com/acme/checkout/OrderHistory.java).
In the profile: one operation, GET /orders/{customer}, 1,090 requests, no errors, p95 about 23 ms; two
statements, each through the service's Database.query wrapper — orders.by-customer from
OrderRepository.findOrders (once per request, 1,090) and order-lines.by-order from
OrderRepository.findLines (once per order, 11,926), both called from OrderService.history; the
requests enter the service's code through the worker loop in main (traced, then
OrderController.history); of the CPU samples inside the requests, about 92% are in
OrderService.summarize.

PASS if the reply names the GET /orders/{customer} operation with a figure, both statements with the
code that issues them (findOrders and findLines, or OrderService.history with their lines), and where
the request runs in the service's code (the entry through main/traced/OrderController, or
OrderService.history and summarize) — read off the profile, not guessed from the source alone.

FAIL if it names an endpoint or statement the profile does not hold, leaves the statements without a
place in the code, or presents the source's structure without anything measured.
