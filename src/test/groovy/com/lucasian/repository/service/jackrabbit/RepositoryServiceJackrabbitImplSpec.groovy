package com.lucasian.repository.service.jackrabbit

import com.lucasian.repository.RepositoryItem
import com.lucasian.repository.service.RepositoryService
import spock.lang.Specification

/**
 * Created by apimentel on 5/6/15.
 */
class RepositoryServiceJackrabbitImplSpec extends Specification {
  RepositoryService repositoryService
  def setup(){
    repositoryService = new RepositoryServiceJackrabbitImpl()
    repositoryService.init()
  }
  def "Should store file"(){
    setup:
    RepositoryItem item = new RepositoryItem(
      name: "testFile",
      path: "/folder/one",
      binary: new ByteArrayInputStream("The file contents".bytes),
      mimeType: "text/plain"
    )
    when:
    repositoryService.storeNode(item)
    then:
    List results = repositoryService.listItemsInPath("/folder/one/")
    results != null
    results.size() == 1
    results.first().name == "testFile"
    results.first().path == "/folder/one/"
  }
  def "Should list items in folder"(){
    setup:
    repositoryService.storeNode(new RepositoryItem(
      name: "testFile",
      path: "/folder/one/two/tree",
      binary: new ByteArrayInputStream("The file contents".bytes),
      mimeType: "text/plain"
    ))
    repositoryService.storeNode(
      new RepositoryItem(
      name: "testFile",
      path: "/folder/one/two",
      binary: new ByteArrayInputStream("The file contents".bytes),
      mimeType: "text/plain"
    )
    )
    when:
    List results = repositoryService.listItemsInPath("/folder/one/two")
    then:
    results != null
    results.size() == 2
  }
}
