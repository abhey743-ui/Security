OpenAPI / Swagger Documentation in Spring Boot — A Practical Guide

This guide explains what API documentation is, why we need it, what OpenAPI and Swagger are, how Spring Boot generates it, and exactly how the annotations in the provided Patient Service code work.

The goal is not just to explain the annotations individually, but to show where they belong, why they belong there, and how the complete documentation flow works.

1. First: What is API Documentation?

An API (Application Programming Interface) lets one software system communicate with another.

For example, your Patient Service exposes an endpoint:

POST /create/patient

Another service, frontend application, mobile application, or developer needs to know:

What URL to call

Which HTTP method to use

What request body to send

What fields are required

What data types the fields have

What validation rules exist

What response is returned

Which HTTP status codes can happen

What errors can occur

What authentication may be required

That information is API documentation.

Without API documentation

A developer may have to:

Read the controller source code.

Find the DTO class.

Guess which fields are mandatory.

Look through service code to understand responses.

Guess error/status-code behavior.

Ask the backend developer questions.

This becomes difficult as the project grows.

With API documentation

The API contract can be displayed in a standard, interactive format.

A developer can open Swagger UI and immediately see something similar to:

POST /create/patient

Request body:
{
  "username": "Aman1234",
  "name": "Raman",
  "age": 14,
  "gender": "Male"
}

Responses:
201 Created
500 Server Error

So API documentation is essentially a human-readable and machine-readable description of how to use an API.

2. What is OpenAPI?

OpenAPI is a specification for describing REST APIs in a standard format.

An OpenAPI document can describe:

endpoints

HTTP methods

path parameters

query parameters

request bodies

request/response schemas

validation-related metadata

status codes

authentication/security schemes

tags/groups

API metadata

The specification is generally represented as JSON or YAML.

A simplified example looks like:

openapi: 3.0.0
info:
  title: Patient Service
  version: v1
paths:
  /create/patient:
    post:
      responses:
        '201':
          description: Patient created

The important point is:

OpenAPI is the specification/contract.

It is not the Swagger UI itself.

3. What is Swagger then?

The word Swagger is commonly used for tools around OpenAPI.

Historically, the specification was called Swagger Specification and was later renamed OpenAPI Specification.

Today, when developers say "Swagger", they may be referring to tools such as:

Swagger UI — interactive web documentation

Swagger Editor — editor for OpenAPI documents

Swagger tooling/ecosystem

In a Spring Boot application, a common setup is:

Your Java code
     |
     v
Springdoc OpenAPI
     |
     v
Generated OpenAPI specification
     |
     +----> JSON/YAML
     |
     +----> Swagger UI

So:

OpenAPI  = API description standard
Swagger  = tooling/ecosystem commonly used to work with OpenAPI
Swagger UI = interactive browser interface for the API documentation

4. Why Do We Need API Documentation?

4.1 Frontend-backend communication

Suppose the frontend team needs to call:

POST /create/patient

The frontend developer can inspect the API documentation instead of asking the backend developer how the endpoint works.

4.2 Microservices communication

Your code is a Patient Service and appears to be part of a microservice architecture.

Other services may need to communicate with it.

For example:

Appointment Service
        |
        | GET patient
        v
Patient Service

The documentation acts as a clear contract between services.

4.3 Testing APIs

Swagger UI is interactive.

A developer can often:

Open Swagger UI.

Select an endpoint.

Click Try it out.

Enter request data.

Execute the request.

View the HTTP response.

This makes it extremely useful for development and testing.

4.4 Team collaboration

Backend developers, frontend developers, QA engineers, DevOps engineers, mobile developers, and integration teams can all use the same API contract.

4.5 Better understanding of a codebase

The annotations placed near your controller and DTO classes make the code self-documenting.

For example:

@Operation(
    description = "It is used to create the patient.",
    summary = "Create a new patient"
)

A developer looking directly at the controller can understand what the endpoint is intended to do.

5. Where Does the Documentation Come From?

There are two major ways to create API documentation.

Option A — Write OpenAPI YAML/JSON manually

You can create a file such as:

openapi.yaml

and manually describe every endpoint.

This is useful in some projects, but it can become repetitive.

Option B — Generate OpenAPI documentation from Java code

This is what your code is doing.

You place annotations such as:

@Operation
@ApiResponse
@Schema
@Tag
@OpenAPIDefinition

on your Java classes and methods.

A library such as springdoc-openapi reads those annotations and Spring MVC information and generates the OpenAPI specification.

This is why the annotations are called documentation metadata.

They do not perform the business operation themselves.

For example:

@PostMapping("/create/patient")

actually maps an HTTP POST request to the controller method.

But:

@Operation(summary = "Create a new patient")

mainly describes the endpoint for API documentation.

6. The Main Pieces in Your Code

Your code uses several OpenAPI annotations.

Here is the big picture:

Application class
    |
    +--> @OpenAPIDefinition
    |       Global API information
    |
    +--> Controller
    |       |
    |       +--> @Tag
    |       +--> @Operation
    |       +--> @ApiResponse / @ApiResponses
    |       |
    |       +--> @PostMapping / @GetMapping
    |
    +--> DTO
            |
            +--> @Schema
            +--> validation annotations

Each part has a different responsibility.

7. @Tag — Grouping Related Endpoints

You wrote:

@Tag(
    name = "Patient controller",
    description = "It provides all the patients related operations"
)
public class PatientController {

What does it do?

@Tag groups related endpoints in Swagger UI.

Your controller contains patient-related operations, so you give the controller a tag:

Patient controller

Swagger UI can then display your patient APIs under this group.

Example

Without tags, a large application could show dozens or hundreds of operations in one large list.

With tags:

Patient controller
    POST /create/patient
    GET  /get/patient

Doctor controller
    POST /create/doctor
    GET  /get/doctor

Appointment controller
    POST /create/appointment
    GET  /get/appointment

This improves discoverability.

Where should @Tag be used?

Usually at the controller class level.

Example:

@RestController
@Tag(name = "Patient APIs", description = "Operations related to patients")
public class PatientController {
}

8. @Operation — Describing an Endpoint

You wrote:

@Operation(
    description = "It is user  to create the patient.",
    summary = "This is to make the patient new details."
)

This annotation describes a specific API operation.

summary

The summary should be a short description.

For example:

summary = "Create a new patient"

This is what users typically see as the concise operation description in Swagger UI.

description

The description can contain more detailed information.

For example:

description = "Creates a patient using the supplied patient information."

Think of it as:

summary     -> short explanation
 description -> detailed explanation

Where should @Operation be used?

At the controller method level, because every HTTP endpoint can have a different description.

Example:

@Operation(
    summary = "Create a new patient",
    description = "Creates a patient from the supplied request data."
)
@PostMapping("/create/patient")
public ResponseEntity<HttpStatus> createPatient(...) {
    ...
}

9. @ApiResponse — Documenting One Possible Response

You wrote:

@ApiResponse(
    responseCode = "201",
    description = "HTTP Status created"
)

This tells OpenAPI:

This operation can return HTTP status 201.

HTTP 201 Created conventionally indicates that a new resource was successfully created.

A better description would be:

@ApiResponse(
    responseCode = "201",
    description = "Patient created successfully"
)

10. @ApiResponses — Documenting Multiple Responses

You also have:

@ApiResponses({
    @ApiResponse(
        description = "Http status Ok",
        responseCode = "200"
    ),
    @ApiResponse(
        description = "Http status not found",
        content = @Content(
            schema = @Schema(
                implementation = ErrorResponse.class
            )
        )
    )
})

@ApiResponses is simply a container for multiple @ApiResponse definitions.

Conceptually:

@ApiResponse       -> one possible response
@ApiResponses      -> multiple possible responses

A cleaner version would explicitly provide the response code for every response:

@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Patient retrieved successfully"
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Patient not found"
    )
})

Notice that in your original second response you did not specify responseCode = "404".

The description says "not found", but OpenAPI should be explicitly told that the status code is 404.

11. @Content — Describing Response Body Content

You have:

@Content(
    schema = @Schema(
        implementation = ErrorResponse.class
    )
)

@Content describes the content of a request or response.

For example, a response may return JSON.

You can describe that JSON with a schema.

Conceptually:

Response
   |
   +--> HTTP status
   |
   +--> Content type (for example application/json)
   |
   +--> Body schema

So @Content helps describe what the body contains.

12. @Schema — Describing Data Models

You use @Schema in your DTO:

@Schema(
    name = "CreatePatient",
    description = "This is to create the new patient"
)
public class CreatePatientDto {

This tells OpenAPI how the DTO should be represented in the generated API documentation.

Swagger UI can then display a model such as:

CreatePatient
-----------------
username : string
name     : string
age      : integer
 gender   : string

The schema is effectively a description of the data structure.

13. @Schema on a Field

You also use it like this:

@Schema(
    description = "This is the username provided by the patient",
    example = "Aman1234"
)
private String username;

Here you are adding documentation specifically for the field.

The important pieces are:

description = "..."
example = "..."

The generated documentation can show developers an example value.

For instance:

username
Type: string
Example: Aman1234
Description: This is the username provided by the patient

14. Validation Annotations Also Help the API Contract

Your DTO contains:

@NotBlank
@Size(max = 10, min = 5)
private String username;

and:

@NotNull
private Integer age;

These are Jakarta Bean Validation annotations, not OpenAPI annotations.

They perform validation when your application handles the request.

For example:

@NotBlank
private String username;

means an empty/blank username should not be accepted.

And:

@Size(max = 10, min = 5)

means the string must be between 5 and 10 characters long.

These constraints can also be reflected in generated OpenAPI documentation when supported/configured by the OpenAPI integration.

So there are two related concepts:

Validation annotation
        |
        +--> protects application input

OpenAPI annotation
        |
        +--> documents the API

They are related, but they are not the same thing.

15. @Valid — Why You Put It on the Request Body

Your controller has:

public ResponseEntity<HttpStatus> createPatient(
        @RequestBody @Valid CreatePatientDto createPatientDto
)

@Valid tells Spring to validate the DTO using the validation annotations inside it.

For example:

@NotBlank
private String username;

and:

@NotNull
private Integer age;

will be checked when the request arrives.

Without @Valid, the validation rules may exist on the DTO but are not automatically triggered for this controller parameter.

16. @RequestBody — What It Means

You wrote:

@RequestBody @Valid CreatePatientDto createPatientDto

@RequestBody tells Spring that the incoming HTTP request body should be converted into a CreatePatientDto object.

For example, the client sends:

{
  "username": "Aman1234",
  "name": "Raman",
  "age": 14,
  "gender": "Male"
}

Spring converts that JSON into:

CreatePatientDto createPatientDto

Then @Valid triggers validation.

The flow is:

HTTP JSON
   |
   v
@RequestBody
   |
   v
CreatePatientDto
   |
   v
@Valid
   |
   v
Validation rules checked

17. @OpenAPIDefinition — Global API Documentation

Your main application class contains:

@OpenAPIDefinition(
    info = @Info(
        title = "PATIENT SERVICE DOCUMENTATION",
        description = "This is patient microservice documentation",
        version = "v1",
        contact = @Contact(
            name = "ABHEY",
            email = "Abhey@gmail.com",
            url = "Abhey.com"
        ),
        license = @License(
            name = "Apche 2.0"
        )
    ),
    externalDocs = @ExternalDocumentation(
        description = "This is the documentation for the patient microservice",
        url = "Abhey.com"
    )
)

This is global API metadata.

It describes the API as a whole rather than just one endpoint.

Think of the difference as:

@OpenAPIDefinition
        |
        +--> Information about the entire API

@Tag
        |
        +--> Information about a group of endpoints

@Operation
        |
        +--> Information about one endpoint

@ApiResponse
        |
        +--> Information about one possible response

@Schema
        |
        +--> Information about one data model

18. Breaking Down @OpenAPIDefinition

18.1 info

The info section contains general information about the API.

info = @Info(...)

18.2 title

title = "PATIENT SERVICE DOCUMENTATION"

This is the title shown in the API documentation.

18.3 description

description = "This is patient microservice documentation"

A general explanation of the API.

18.4 version

version = "v1"

This is the API documentation/specification version.

Do not automatically assume this is your application's build/version number. It is normally used as the API contract version.

19. @Contact

You wrote:

contact = @Contact(
    name = "ABHEY",
    email = "Abhey@gmail.com",
    url = "Abhey.com"
)

This supplies contact information for the API documentation.

It tells users who they can contact about the API.

For production documentation, use valid URLs such as:

https://example.com

rather than placeholder values like:

Abhey.com

20. @License

You wrote:

license = @License(name = "Apche 2.0")

This declares the license associated with the API.

A better spelling would be:

license = @License(name = "Apache 2.0")

In a real project, make sure the license information actually reflects the legal terms of your project.

21. @ExternalDocumentation

You have:

externalDocs = @ExternalDocumentation(
    description = "This is the documentation for the patient microservice",
    url = "Abhey.com"
)

This points Swagger/OpenAPI users to additional documentation outside the API specification.

For example, this could point to:

https://your-company.com/docs/patient-service

It is optional.

22. How Everything Connects Together

Your application follows this overall structure:

                        Patient Application
                               |
                               v
                    @OpenAPIDefinition
                               |
                    Global API information
                               |
                +--------------+--------------+
                |                             |
                v                             v
        PatientController                 DTO classes
                |                             |
             @Tag                         @Schema
                |                             |
          Endpoint methods                 Fields
                |                             |
        +-------+--------+                    +----------+
        |                |                    |
        v                v                    v
 @Operation       @ApiResponses        validation metadata
        |                |
        v                v
  Endpoint docs     Response docs

Springdoc then uses this information to generate the OpenAPI definition and Swagger UI.

23. The Important Missing Piece: Springdoc

Annotations alone do not magically create Swagger UI.

You normally need an OpenAPI integration library for Spring Boot, commonly springdoc-openapi.

For a Maven project, a common dependency for Spring MVC applications is:

<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>YOUR_COMPATIBLE_VERSION</version>
</dependency>

The exact version should match your Spring Boot generation and project setup. Avoid blindly copying an old version from a tutorial.

For Gradle, the equivalent dependency is:

dependencies {
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:YOUR_COMPATIBLE_VERSION'
}

Once Springdoc is on the classpath, it can inspect your Spring MVC controllers and OpenAPI annotations and generate documentation.

24. What Happens at Runtime?

The high-level process is:

1. Application starts
        |
        v
2. Spring Boot registers controllers
        |
        v
3. Springdoc inspects Spring endpoints
        |
        v
4. Springdoc reads OpenAPI annotations
        |
        v
5. OpenAPI document is generated
        |
        +------> /v3/api-docs
        |
        +------> Swagger UI

The generated specification commonly includes an endpoint such as:

/v3/api-docs

and Swagger UI is commonly exposed at a URL similar to:

/swagger-ui/index.html

The exact URLs can be customized through Springdoc configuration.

25. What Would Swagger UI Show for Your Controller?

Your controller has:

@RestController
@Tag(name = "Patient controller")
public class PatientController {

So Swagger UI can organize the endpoints under a patient-related section.

You have:

@PostMapping("/create/patient")

so the UI can show:

POST /create/patient

You also have:

@GetMapping("/get/patient")

so the UI can show:

GET /get/patient

Because the POST method accepts:

CreatePatientDto

the schema generated from CreatePatientDto can appear as the request model.

26. Your DTO and OpenAPI Documentation

Your DTO is:

@Schema(
    name = "CreatePatient",
    description = "This is to create the new patient"
)
public class CreatePatientDto {

and contains fields such as:

private String username;
private String name;
private Integer age;
private String gender;

The combination of the Java types, validation annotations, and schema annotations helps produce a model similar to:

CreatePatient

username
    type: string
    example: Aman1234
    min/max length: 5-10
    required: yes

name
    type: string
    example: Raman
    required: yes

age
    type: integer
    example: 14
    required: yes

gender
    type: string
    example: Male
    required: yes

Exactly which constraints appear in generated documentation depends on the OpenAPI integration and the project's configuration/version.

27. A Better Version of Your CreatePatientDto

A cleaned-up version could look like:

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(
        name = "CreatePatient",
        description = "Request body used to create a patient"
)
public class CreatePatientDto {

    @Schema(
            description = "Username provided by the patient",
            example = "Aman1234"
    )
    @NotBlank(message = "Username cannot be blank")
    @Size(
            min = 5,
            max = 10,
            message = "Username must be between 5 and 10 characters"
    )
    private String username;

    @Schema(
            description = "Name of the patient",
            example = "Raman"
    )
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @Schema(
            description = "Age of the patient",
            example = "14"
    )
    @NotNull(message = "Age cannot be null")
    private Integer age;

    @Schema(
            description = "Gender of the patient",
            example = "Male"
    )
    @NotBlank(message = "Gender cannot be blank")
    private String gender;
}

This keeps the Java validation behavior and documentation close to the fields they describe.

28. A Better Version of Your Controller Documentation

Here is a cleaner version of the controller pattern:

@RestController
@AllArgsConstructor
@Tag(
        name = "Patient APIs",
        description = "Operations related to patients"
)
public class PatientController {

    private final PatientServiceInterface patientServiceInterface;

    @Operation(
            summary = "Create a new patient",
            description = "Creates a patient using the supplied patient information."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Patient created successfully"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @PostMapping("/create/patient")
    public ResponseEntity<HttpStatus> createPatient(
            @RequestBody @Valid CreatePatientDto createPatientDto
    ) throws ServerException {

        Boolean isCreated = patientServiceInterface.createPatient(createPatientDto);

        if (!isCreated) {
            throw new ServerException("There is a server exception. Please try later.");
        }

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Operation(
            summary = "Get patient details",
            description = "Fetches patient details using the patient user ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Patient retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Patient not found"
            )
    })
    @GetMapping("/get/patient")
    public GetPatientData getPatient(@RequestParam("userId") Long userId) {
        return patientServiceInterface.getPatientData(userId);
    }
}

There are some important corrections here, which are explained below.

29. Important Correction: @RequestParam vs @QueryParam

Your original code imports:

import jakarta.ws.rs.QueryParam;

and uses:

@GetMapping("/get/patient")
public GetPatientData getPatient(@QueryParam("userId") Long userId)

This is a mismatch in a normal Spring MVC controller.

For Spring MVC, the usual annotation is:

import org.springframework.web.bind.annotation.RequestParam;

and:

public GetPatientData getPatient(@RequestParam("userId") Long userId)

Then a request can look like:

GET /get/patient?userId=10

So for your Spring Boot controller, prefer:

@RequestParam("userId")

unless you deliberately configured and use JAX-RS instead of standard Spring MVC request parameter binding.

30. Important Correction: Response Documentation Should Match the Actual Method

Your method returns:

public GetPatientData getPatient(...)

That means the successful response is a GetPatientData object.

To document the response body more precisely, you can specify its schema:

@ApiResponse(
    responseCode = "200",
    description = "Patient retrieved successfully",
    content = @Content(
        schema = @Schema(implementation = GetPatientData.class)
    )
)

This is especially useful when you want the documentation to clearly show the JSON structure returned by the endpoint.

31. Important Correction: ErrorResponse May Not Be the Best Error Schema

You imported:

import org.springframework.web.ErrorResponse;

and used:

@Schema(implementation = ErrorResponse.class)

Be careful here.

ErrorResponse is a Spring framework interface/type related to error handling; it is not automatically the same thing as the JSON error body your application actually returns.

A better design for a real application is often to create your own error DTO, for example:

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(example = "404")
    private int status;

    @Schema(example = "Patient not found")
    private String message;

    @Schema(example = "/get/patient")
    private String path;
}

Then document it explicitly:

@ApiResponse(
    responseCode = "404",
    description = "Patient not found",
    content = @Content(
        schema = @Schema(implementation = ApiErrorResponse.class)
    )
)

That way your Swagger documentation matches your application's real error contract.

32. Important Correction: Return Type for 201 Created

Your method currently returns:

ResponseEntity<HttpStatus>

and then:

return new ResponseEntity<>(HttpStatus.CREATED);

This works as a response status design, but many APIs return either:

the created resource

a response DTO

a ResponseEntity<Void> when no response body is required

For example:

return ResponseEntity.status(HttpStatus.CREATED).build();

If there is no response body, ResponseEntity<Void> communicates intent more clearly.

For API documentation, you should document what the endpoint actually returns, rather than documenting a response body that does not exist.

33. Important Correction: @SpringBootApplication

Your pasted application class appears to have:

,pringBootApplication

This looks like a typo in the pasted code.

The annotation should be:

@SpringBootApplication

For example:

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "PATIENT SERVICE DOCUMENTATION",
        description = "Patient microservice API documentation",
        version = "v1"
    )
)
public class ThisIsPatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThisIsPatientApplication.class, args);
    }
}

34. What Belongs on Which Class?

This is one of the most important things to remember.

Main application class

Use global metadata here:

@OpenAPIDefinition

Typical things:

API title

API description

API version

contact

license

external documentation

Controller class

Use grouping information here:

@Tag

Example:

@Tag(
    name = "Patient APIs",
    description = "Operations related to patients"
)

Controller method

Use endpoint-level information here:

@Operation
@ApiResponse
@ApiResponses

And of course the Spring MVC endpoint mapping:

@GetMapping
@PostMapping
@PutMapping
@DeleteMapping

DTO class

Use model/data documentation here:

@Schema

DTO field

Use field descriptions/examples here:

@Schema(description = "...", example = "...")

Use validation annotations such as:

@NotBlank
@NotNull
@Size
@Min
@Max
@Email

for actual input validation.

35. A Simple Mental Model

Remember this hierarchy:

API
 |
 +-- Global information
 |     `-- @OpenAPIDefinition
 |
 +-- Group of endpoints
 |     `-- @Tag
 |
 +-- Endpoint
 |     +-- @Operation
 |     +-- @ApiResponses
 |     |     `-- @ApiResponse
 |     `-- Spring mapping
 |           +-- @GetMapping
 |           +-- @PostMapping
 |           +-- @PutMapping
 |           `-- @DeleteMapping
 |
 `-- Data model
       `-- @Schema
             +-- fields
             `-- examples/descriptions

That mental model makes most OpenAPI annotations easier to understand.

36. How to Implement OpenAPI Documentation from Scratch

Step 1 — Create your Spring Boot project

Start with a normal Spring Boot application using Spring MVC.

Step 2 — Add the Springdoc dependency

For Maven:

<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>YOUR_COMPATIBLE_VERSION</version>
</dependency>

Use a version compatible with your Spring Boot version.

Step 3 — Create your controller normally

For example:

@RestController
public class PatientController {

    @PostMapping("/create/patient")
    public ResponseEntity<Void> createPatient(...) {
        ...
    }
}

At this point Spring knows the endpoint exists.

Step 4 — Add documentation metadata

Add:

@Tag
@Operation
@ApiResponses
@ApiResponse

where appropriate.

Step 5 — Document your DTOs

Add:

@Schema

and useful field-level examples/descriptions.

Step 6 — Add global API metadata

Use:

@OpenAPIDefinition

on the application/configuration class when you want to customize the API's overall information.

Step 7 — Start the application

Run your Spring Boot application.

Step 8 — Open Swagger UI

With the standard Springdoc setup, Swagger UI is commonly available at:

http://localhost:8080/swagger-ui/index.html

The exact path can differ if you customize it.

Step 9 — Inspect the generated OpenAPI document

The machine-readable API description is commonly exposed at:

http://localhost:8080/v3/api-docs

This is useful because tools can consume the OpenAPI document programmatically.

37. Swagger UI vs OpenAPI JSON

These are not the same thing.

OpenAPI JSON/YAML

This is the machine-readable API contract.

Example:

{
  "openapi": "3.0.1",
  "info": {
    "title": "Patient Service",
    "version": "v1"
  },
  "paths": {}
}

Machines and other tools can consume this.

Swagger UI

This is the interactive visual interface that renders the OpenAPI document for humans.

So:

OpenAPI document
      |
      v
Swagger UI renders it
      |
      v
Developer sees interactive API docs

38. Why OpenAPI Is Better Than Writing a README Alone

A normal README can explain your API, but OpenAPI provides a standardized machine-readable structure.

A README might say:

Use POST /create/patient

but OpenAPI can formally describe:

Path:
/create/patient

Method:
POST

Request content type:
application/json

Request schema:
CreatePatient

Success response:
201

Error responses:
400
500

Because it is structured, other tools can use it too.

39. What Can Tools Do With an OpenAPI Specification?

Once your API is correctly described in OpenAPI, the specification can be used by tooling for things such as:

interactive API documentation

client SDK generation

server stub generation

API testing workflows

contract validation

API governance

integration with API gateways and developer portals

generating examples and schemas

This is one of the main reasons standardized API descriptions are valuable.

40. Documentation vs Business Logic

A very important distinction:

@Operation(summary = "Create patient")

does not create a patient.

This does:

patientServiceInterface.createPatient(createPatientDto);

Similarly:

@ApiResponse(responseCode = "201")

does not cause the application to return 201.

This does:

return ResponseEntity.status(HttpStatus.CREATED).build();

So think of OpenAPI annotations as description, not business behavior.

41. Documentation vs Validation

Another important distinction:

@NotBlank

is primarily a validation rule.

@Schema(example = "Aman1234")

is primarily documentation metadata.

You can think of it this way:

@NotBlank
    |
    +--> Is the request valid?

@Size
    |
    +--> Is the request valid?

@Schema
    |
    +--> How should the data be described in API docs?

@Operation
    |
    +--> How should this endpoint be described?

42. What You Should Document for Every Important Endpoint

For production-grade API documentation, try to make each important endpoint clear about:

Endpoint purpose

@Operation(summary = "Create a new patient")

Request parameters

Document path/query parameters when needed.

Request body

Document DTO/schema and useful examples.

Success response

Document status and response body.

Client errors

For example:

400 Bad Request
404 Not Found
409 Conflict

Server errors

For example:

500 Internal Server Error

Security

Document authentication/authorization requirements when applicable.

43. A Stronger Patient Endpoint Example

A more complete example could look like:

@Operation(
        summary = "Create a new patient",
        description = "Creates a new patient using the supplied patient details."
)
@ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Patient created successfully"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid patient data",
                content = @Content(
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = @Content(
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        )
})
@PostMapping("/create/patient")
public ResponseEntity<Void> createPatient(
        @RequestBody @Valid CreatePatientDto createPatientDto
) {
    boolean isCreated = patientServiceInterface.createPatient(createPatientDto);

    if (!isCreated) {
        throw new RuntimeException("There is a server exception. Please try later.");
    }

    return ResponseEntity.status(HttpStatus.CREATED).build();
}

The exact exception design should be handled with a proper exception handler in a real application, rather than relying on a generic runtime exception.

44. Recommended Architecture for Error Documentation

For larger projects, consider creating a common error model:

@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(example = "400")
    private int status;

    @Schema(example = "Validation failed")
    private String message;

    @Schema(example = "/create/patient")
    private String path;
}

Then all controllers can document errors consistently.

For example:

PatientController
      |
      +---- 400 -> ApiErrorResponse
      +---- 404 -> ApiErrorResponse
      +---- 500 -> ApiErrorResponse

AppointmentController
      |
      +---- 400 -> ApiErrorResponse
      +---- 404 -> ApiErrorResponse
      +---- 500 -> ApiErrorResponse

That is much cleaner than inventing a different error format in every controller.

45. Where to Put the OpenAPI Configuration

You can keep simple global metadata on your main application class:

@SpringBootApplication
@OpenAPIDefinition(...)
public class ThisIsPatientApplication {
}

For more advanced projects, it is often cleaner to separate API documentation configuration into its own configuration class.

For example:

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Patient Service API",
                version = "v1",
                description = "API documentation for the Patient Service"
        )
)
public class OpenApiConfig {
}

This keeps the application bootstrap class focused on starting the application.

The exact configuration style is a project organization choice.

46. Typical Project Structure

A project can be organized like this:

src/main/java/com/patients
│
├── Controller
│   └── PatientController.java
│
├── Dto
│   ├── PatientRequestDto
│   │   └── CreatePatientDto.java
│   └── PatientResponseDto
│       └── GetPatientData.java
│
├── Service
│   └── PatientServiceInterface.java
│
├── Config
│   └── OpenApiConfig.java
│
└── ThisIsPatientApplication.java

A separate OpenApiConfig class becomes especially useful as the documentation grows.

47. Common Mistakes to Avoid

Mistake 1 — Missing the Springdoc dependency

Annotations may compile, but you still need the OpenAPI integration dependency to expose the generated specification/UI.

Mistake 2 — Using the wrong parameter annotation

For standard Spring MVC controllers, prefer:

@RequestParam

instead of accidentally mixing in:

jakarta.ws.rs.QueryParam

Mistake 3 — Documenting the wrong response code

Do not write:

@ApiResponse(description = "Not found")

and forget the status code.

Use:

@ApiResponse(
    responseCode = "404",
    description = "Patient not found"
)

Mistake 4 — Documenting a response body that is not actually returned

If an endpoint returns no body, do not document a JSON body just for the sake of having one.

Documentation should match real behavior.

Mistake 5 — Putting everything in @OpenAPIDefinition

@OpenAPIDefinition is for global metadata.

Do not use it as a replacement for endpoint-level @Operation documentation.

Mistake 6 — Over-documenting obvious implementation details

OpenAPI documentation should explain the API contract, not expose every internal service implementation detail.

For example, this is useful:

Creates a patient.

But this is generally unnecessary:

Calls PatientRepository.save() after invoking method X.

The API consumer needs the contract, not your internal implementation.

48. A Simple Rule for Choosing the Annotation

When you are unsure which annotation to use, ask:

"Am I documenting the whole API?"

Use:

@OpenAPIDefinition

"Am I grouping controller endpoints?"

Use:

@Tag

"Am I describing one endpoint?"

Use:

@Operation

"Am I describing one possible HTTP response?"

Use:

@ApiResponse

"Am I describing several possible responses?"

Use:

@ApiResponses

"Am I describing JSON/model structure?"

Use:

@Schema

"Am I validating request input?"

Use Bean Validation annotations such as:

@NotBlank
@NotNull
@Size
@Min
@Max
@Email

49. How to Read Your Own Code as Documentation

Here is your code translated into plain English.

@RestController

This class exposes HTTP API endpoints.

@Tag(name = "Patient controller")

Group these endpoints under Patient controller in the API documentation.

@PostMapping("/create/patient")

There is a POST endpoint at /create/patient.

@Operation(summary = "Create a new patient")

The endpoint creates a patient.

@RequestBody

The client sends data in the HTTP request body.

@Valid

Validate that request body using Bean Validation rules.

CreatePatientDto

The request follows the CreatePatient data model.

@Schema

Describe that model/field for OpenAPI documentation.

@NotBlank
@Size
@NotNull

Enforce input constraints.

@ApiResponses

Document the possible HTTP outcomes.

@OpenAPIDefinition

Describe the overall Patient Service API.

That is essentially what your code is doing.

50. Final Mental Picture

The entire setup can be remembered like this:

                    YOUR SPRING BOOT APP
                              |
                              v
                     REST CONTROLLERS
                              |
             +----------------+----------------+
             |                                 |
             v                                 v
      Spring MVC mappings                 OpenAPI annotations
       @GetMapping                         @Operation
       @PostMapping                        @ApiResponse
       @PutMapping                         @Schema
       @DeleteMapping                      @Tag
             |                             @OpenAPIDefinition
             |                                 |
             +----------------+----------------+
                              |
                              v
                      SPRINGDOC OPENAPI
                              |
             +----------------+----------------+
             |                                 |
             v                                 v
      OpenAPI JSON/YAML                   Swagger UI
      machine-readable                    human-friendly
             |                                 |
             v                                 v
       Other tools                     Developers/testers

The most important concept is:

Your Spring Boot code defines the actual API, and OpenAPI annotations describe that API so tools and humans can understand the contract.

51. Quick Reference Table

Annotation

Usually placed on

Purpose

@OpenAPIDefinition

Application/config class

Global API metadata

@Info

Inside @OpenAPIDefinition

API title, version, description, etc.

@Contact

Inside @Info

API contact information

@License

Inside @Info

License metadata

@ExternalDocumentation

@OpenAPIDefinition

Link to extra documentation

@Tag

Controller class

Groups related endpoints

@Operation

Controller method

Describes one endpoint

@ApiResponses

Controller method

Holds multiple response definitions

@ApiResponse

Inside @ApiResponses or on method

Describes one response/status

@Content

Response/request documentation

Describes payload content

@Schema

DTO class/field

Describes data model/field

@RequestBody

Controller parameter

Binds HTTP body to Java object

@RequestParam

Controller parameter

Binds query parameter

@Valid

Request object parameter

Triggers Bean Validation

@NotBlank

DTO field

Validates non-blank string

@NotNull

DTO field

Validates non-null value

@Size

DTO field

Validates string/collection size

52. What I Would Change in Your Current Code

The most important improvements are:

Use Spring's @RequestParam for the userId query parameter in a normal Spring MVC controller.

Give every documented response an explicit responseCode.

Document the actual success response model, especially for GetPatientData.

Consider defining your own ApiErrorResponse DTO instead of documenting a generic Spring error abstraction.

Use clear summary and description text rather than grammatical/ambiguous descriptions.

Keep API documentation metadata separate from business logic.

Ensure the Springdoc dependency version is compatible with your Spring Boot version.

Use valid URLs for contact and external documentation fields.

Keep @OpenAPIDefinition for global API metadata and controller annotations for endpoint-specific documentation.

Make sure @SpringBootApplication is correctly written.

53. The One-Sentence Definition to Remember for Interviews

If someone asks:

"What is OpenAPI documentation and why do we use it?"

A strong answer is:

OpenAPI is a standardized way to describe an HTTP API, and in Spring Boot we can use tools such as Springdoc together with OpenAPI annotations to generate machine-readable API specifications and interactive Swagger UI documentation, making the API contract easier to understand, test, integrate, and maintain.

54. The One-Sentence Difference Between the Main Concepts

Remember this:

API             = the actual interface your application exposes
API documentation = explanation/contract describing that interface
OpenAPI         = standard format for that description
Springdoc       = library that can generate the OpenAPI description from Spring code
Swagger UI      = web UI that displays the OpenAPI description interactively

That is the core of what you are implementing in your Patient Service.
