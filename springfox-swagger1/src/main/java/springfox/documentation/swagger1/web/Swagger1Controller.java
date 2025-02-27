/*
 *
 *  Copyright 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package springfox.documentation.swagger1.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;
import springfox.documentation.service.Documentation;
import springfox.documentation.spring.web.DocumentationCache;
import springfox.documentation.spring.web.json.Json;
import springfox.documentation.spring.web.json.JsonSerializer;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger1.dto.ApiListing;
import springfox.documentation.swagger1.dto.ResourceListing;
import springfox.documentation.swagger1.mappers.ServiceModelToSwaggerMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.*;
import static java.util.stream.Collectors.*;
import static springfox.documentation.swagger1.mappers.Mappers.*;
import static springfox.documentation.swagger1.web.ApiListingMerger.*;

@RestController
@ApiIgnore
@RequestMapping("${springfox.documentation.swagger.v1.path:/api-docs}")
public class Swagger1Controller {

  private final DocumentationCache documentationCache;
  private final ServiceModelToSwaggerMapper mapper;
  private final JsonSerializer jsonSerializer;

  @Autowired
  public Swagger1Controller(
      DocumentationCache documentationCache,
      ServiceModelToSwaggerMapper mapper,
      JsonSerializer jsonSerializer) {

    this.documentationCache = documentationCache;
    this.mapper = mapper;
    this.jsonSerializer = jsonSerializer;
  }

  @RequestMapping(method = RequestMethod.GET)
  public ResponseEntity<Json> getResourceListing(
      @RequestParam(value = "group", required = false) String swaggerGroup) {

    return getSwaggerResourceListing(swaggerGroup);
  }

  @RequestMapping(value = "/{swaggerGroup}/{apiDeclaration}", method = RequestMethod.GET)
  public ResponseEntity<Json> getApiListing(
      @PathVariable String swaggerGroup,
      @PathVariable String apiDeclaration,
      HttpServletRequest servletRequest) {

    return getSwaggerApiListing(swaggerGroup, apiDeclaration, servletRequest);
  }

  private ResponseEntity<Json> getSwaggerApiListing(
      String swaggerGroup,
      String apiDeclaration,
      HttpServletRequest servletRequest) {

    String groupName = ofNullable(swaggerGroup).orElse("default");
    Documentation documentation = documentationCache.documentationByGroup(groupName);
    if (documentation == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    Map<String, List<springfox.documentation.service.ApiListing>> apiListingMap = documentation.getApiListings();
    Map<String, Collection<ApiListing>> dtoApiListings
        = apiListingMap.entrySet().stream()
        .map(toApiListingDto(servletRequest, documentation.getHost(), mapper))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    Collection<ApiListing> apiListings = dtoApiListings.get(apiDeclaration);
    return mergedApiListing(apiListings)
        .map(jsonSerializer::toJson)
        .map(toResponseEntity())
        .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  private ResponseEntity<Json> getSwaggerResourceListing(String swaggerGroup) {
    String groupName = ofNullable(swaggerGroup).orElse(Docket.DEFAULT_GROUP_NAME);
    Documentation documentation = documentationCache.documentationByGroup(groupName);
    if (documentation == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    springfox.documentation.service.ResourceListing listing = documentation.getResourceListing();
    ResourceListing resourceListing = mapper.toSwaggerResourceListing(listing);

    return ofNullable(jsonSerializer.toJson(resourceListing))
        .map(toResponseEntity())
        .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  private <T> Function<T, ResponseEntity<T>> toResponseEntity() {
    return input -> new ResponseEntity<>(input, HttpStatus.OK);
  }
}
