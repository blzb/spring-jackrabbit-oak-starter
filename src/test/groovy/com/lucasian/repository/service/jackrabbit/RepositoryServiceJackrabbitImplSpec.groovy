package com.lucasian.repository.service.jackrabbit

import com.lucasian.repository.RepositoryItem
import com.lucasian.repository.service.RepositoryService
import spock.lang.IgnoreRest
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
    RepositoryItem item = getTestNode("testFile","/folder/one")
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
    repositoryService.storeNode(getTestNode("testFile","/folder/one/two/tree"))
    repositoryService.storeNode(getTestNode("testFile", "/folder/one/two",))
    when:
    List results = repositoryService.listItemsInPath("/folder/one/two")
    then:
    results != null
    results.size() == 2
  }

  def "Should retrieve file"(){
    setup:
    repositoryService.storeNode(getTestNode("testFile", "/folder/one/"))
    when:
    Map result = repositoryService.getContent("/folder/one/testFile")
    then:
    result.mime == "text/plain"
    result.stream != null
  }

  def "Should store versioned content"(){
    setup:
    repositoryService.storeNode(getTestNode("testFile", "/folder/one/"))
    repositoryService.storeNode(getTestNode("testFile", "/folder/one/"))
    repositoryService.storeNode(getTestNode("testFile", "/folder/one/"))
    when:
    Map first = repositoryService.getVersionContent("/folder/one/testFile", "1.0")
    Map second = repositoryService.getVersionContent("/folder/one/testFile", "1.1")
    Map third = repositoryService.getVersionContent("/folder/one/testFile", "1.2")
    then:
    first.mime == "text/plain"
    second.mime == "text/plain"
    third.mime == "text/plain"
  }
  def getTestNode(String name, String path){
    new RepositoryItem(
      name: name,
      path: path,
      binary: new ByteArrayInputStream((name+path).bytes),
      mimeType: "text/plain"
    )
  }
}
