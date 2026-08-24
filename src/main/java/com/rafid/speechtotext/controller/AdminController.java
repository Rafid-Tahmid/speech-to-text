/*
 * These two annotations combine to create the required URL:
  @RequestMapping("/api/v1/admin")
  @GetMapping("/uptime")
  The resulting path is: 	/api/v1/admin/uptime 
 */

package com.rafid.speechtotext.controller;

  import com.rafid.speechtotext.model.UptimeResponse;
  import com.rafid.speechtotext.service.ServerLifecycleService;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;

  @RestController	//This class receives web requests, and its returned objects should be sent back as response data as JSON.
  @RequestMapping("/api/v1/admin")
  public class AdminController {	//  is constructor dependency injection. Spring creates the service and supplies it to the controller.

      private final ServerLifecycleService lifecycleService;

      public AdminController(ServerLifecycleService lifecycleService) {
          this.lifecycleService = lifecycleService;
      }

      @GetMapping("/uptime")
      public UptimeResponse getUptime() {
          return lifecycleService.getUptime();
      }
  }
