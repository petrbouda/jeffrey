---
type: llm
focus: trace
---

Judge the answer the agent gave the user — its replies, not the tool results; the answer may be
followed by a separate offer to implement a fix.

The working directory is the service's repository (src/main/java/com/acme/checkout/OrderHistory.java).
In the profile, the statement order-lines.by-order ran 11,926 times in 1,090 traced GET /orders/{customer}
requests — a median of 11 per request, at most 16 — issued from OrderRepository.findLines (line 185),
called once per order from the loop in OrderService.history (line 140), through the service's own
Database.query wrapper. orders.by-customer ran once per request (1,090).

PASS if the reply says there is an N+1, names findLines and the per-order loop in OrderService.history
(by method or by line), gives the per-request count (the median, the maximum, or the 11,926 against
1,090 requests) with what it counts, and proposes fetching the lines of all the customer's orders in
one query (an IN list, a join or a batch) — offered, not done.

FAIL if it says there is no N+1, blames orders.by-customer or the Database wrapper itself as the
repeated statement, gives no per-request figure, or says it changed the code.
