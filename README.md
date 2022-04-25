# AutoSteam
```code
Due to the deprecated of relevant usage scenarios, this project is now making public 
with no hardcode credentials are still valid, and it's no longer maintained.
```

### Intro
 - This project provides a full and a half automated solution for automatically registering tons of Steam accounts. It can also used to pass China's Real-Name Authorization.    
 - Those accounts can be managed to buy free games, change profiles and join groups.
 - Some Remote HTTP api and panel is also provided for managing the accounts. 

### Module Description
 - <b>JumpServer</b> is a simple naive HTTP facing proxy service used to bypass China GFW in order to access steam community.
 - <b>JumpServer Central</b> is a manager for multiple online servers by doing ssh connections, uploading programs, and running commands.
 - <b>KSoup</b> An Addon for JSoup in order to run all HTTP request under kotlin coroutine.
 - <b>accountsjar</b> provide remote account management 
 - <b>/src/extensions/</b> provide chrome extension to achieve half automated
 - <b>/src/</b> contains registration logic and codes.
