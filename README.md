# AI Natural Language to SQL System

## Overview

This project is an AI-driven backend system that converts natural language queries into safe PostgreSQL SQL statements using Large Language Models (LLMs). The goal is to allow users to query databases using plain English while ensuring the generated SQL is validated, secure, and production-ready.
The system is built using Spring Boot and follows an event-driven architecture for scalable and asynchronous query processing.



## Architecture

The system uses a distributed architecture where user requests are processed asynchronously.
https://github.com/ritvikg21/NL2SQL/blob/main/NL2SQLArchitecture.jpg

# Flow:

1. User submits a natural language query through REST API.
2. Request is stored as a job with lifecycle status.
3. Kafka processes events for SQL generation and execution.
4. LLM converts the query into SQL.
5. SQL is validated and executed against PostgreSQL.
6. Results are returned and cached in Redis.



## Tech Stack

* Java
* Spring Boot
* Apache Kafka
* PostgreSQL
* Redis
* JWT Authentication
* Docker (optional)



## Key Features

### Natural Language to SQL

Uses LLMs to convert user queries into safe PostgreSQL SQL statements.

### Asynchronous Processing

Kafka-based event-driven architecture enables scalable background processing.

### Redis Caching

Reduces repeated LLM calls and improves response performance.

### Job Lifecycle Management

Each request moves through job states:

* SUBMITTED
* PROCESSING
* COMPLETED
* FAILED

### Fault Tolerance

Retry mechanisms and structured error handling ensure reliability.

### Security

JWT-based authentication with Role-Based Access Control (RBAC):

* ADMIN → database operations
* USER → read-only queries



## Design Patterns Used

The system uses several software design patterns for modular architecture:

* Singleton
* Strategy
* Factory
* Adapter
* Observer



## Running the Project

### Prerequisites

* Java 17+
* PostgreSQL
* Redis
* Apache Kafka

### Steps

Clone repository
Start required services
Run the application



## API Example

Example request:
POST /query
{
"query": "Show total sales for last month"
}
Response:
{
"status": "PROCESSING",
"jobId": "12345"
}

