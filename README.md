# Hunter Douglas PowerView Hub bridge for SmartThings

## Overview

This project implements a SmartApp and device handlers to allow you to directly communicate with the PowerView hub on your local network and do the following:
* auto-discover registered shades
* auto-discover configured scenes
* set/monitor the shade level
* set/monitor the vanes level
* execute scenes

The benefit of using this is that there is no requirement for the default Powerview Connect web service or other middlemen like IFTTT--your SmartThings hub will communicate directly with the PowerView hub. **Note:** due to the way SmartThings works, a connection to the ST cloud is still required.

This project currently does *not* support:
* reading/editing rooms
* modifying schedules set in PV
* editing scenes
* reading/editing scene collections
* managing PowerView Connect settings

This is primarily for [Hunter Douglas Silhouette shades](https://www.hunterdouglas.com/silhouette) so there is both the vane and shade control. Other shades will likely work, though there will be extraneous functionality that may throw errors. Relying on scenes is likely the easiest way forward in this case.

## Screenshots
Controlling a shade

![screen1](./assets/screen-08.png) 

Controlling a scene

![screen1](./assets/screen-09.png)

## Installation
This requires you to log in to the SmartThings web-based IDE to add a custom SmartApp and two custom device handlers.

1. Register the following custom components (see tutorial for SmartApps and [device handlers](https://www.thesmartesthouse.com/blogs/the-smartest-blog/how-to-install-a-custom-device-handler-in-smartthings)):
   1. Install the `Hunter Douglas PowerView` SmartApp from this repo: owner=`johnvey`, name=`smartthings-hd-powerview`, branch=`master`
   1. Install the `Hunter Douglas PowerView Shade` device handler from this repo
   1. Install the `Hunter Douglas PowerView Scene` device handler from this repo
1. Add the newly registered SmartApp via *Automation* > *SmartApps* > *Add a SmartApp* > *My Apps* section
1. Open the app

   ![screen1](./assets/screen-01.png)
1. Enter the IP address of your PowerView hub, click "Done"
  
   ![screen1](./assets/screen-03.png)
1. Wait for the app to discover your shades and scenes

   ![screen1](./assets/screen-04.png) ![screen1](./assets/screen-05.png)
1. Choose the shades you want to control via SmartThings

   ![screen1](./assets/screen-06.png)
1. Choose the scenes you want to control via SmartThings

   ![screen1](./assets/screen-07.png)

## Implementation Details

The PowerView hub is a very simple HTTP server. I've documented the API that I've been able to sniff from a hub at `Rev. 1, SubRev. 1, Build 847`:

[PowerView hub API](https://github.com/johnvey/smartthings-hd-powerview/blob/master/powerview_hub_api.md)

The structure of this SmartThings handler is:
* 1 SmartApp that handles the initial setup and discovery of the connected shades and configured scenes
* 1 device handler instance for each shade
* 1 device handler instance for each scene

## SmartThings Programming Errata

Programming these handlers was markedly cumbersome due to the poor quality of the documentation. Triggering the HTTP request was suprisingly the most opaque, and the community boards were rife with contradicting information and cargo culting. I've compiled what I think are the correct variants on making local LAN HTTP requests:

[Making local LAN HTTP requests from SmartThings hub](https://github.com/johnvey/smartthings-dev-lan-http)

### **Tip 1:** If you are connecting your GitHub project directly to the ST IDE, make sure your source code file name matches the `name` field in the metadata declaration exactly.
If you fail to do this, attempting to use the "Update from repo" functionality will fail with no further explanation. [Official doc mention](http://docs.smartthings.com/en/latest/tools-and-ide/github-integration.html#step-5-configure-git-to-sync-fork-with-smartthings).

### **Tip 2:** Direct embed of a `dynamicPage()` inside of the top-level `preferences()` block for a SmartApp doesn't work.
Instead of something like
```
preferences {
    dynamicPage(...)
}
```
use a separate function like:
```
preferences {
    page(name: "singlePagePref")
}
def singlePagePref() {
    return dynamicPage() {...}
}
```

### **Tip 3:** You can use many of the other Groovy language features.
You can use `import` to bring in other functionality that makes programming in ST more palatable. For example, I wanted to declare a top-scope "enum", but ran into the sandboxing that ST uses via a [class wrapper around your SmartApp](http://docs.smartthings.com/en/latest/getting-started/groovy-for-smartthings.html#how-it-works). The solution to this was to [use the `@Field` decorator](https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy) to effectively hoist my `def`s out of the default scope.