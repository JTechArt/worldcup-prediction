## Make application multitenant. 

I undertand that this is internal project, but in case if this website is requested to be used we can suggest group layer. 
example: 
mydomain.com/{group}/ - url will represent the different group. 
and security should applied for that specific group. If I am in GroupA and GroupB by switched to one group to another just from teh header select the right group 

How to became group memeber - just like a login , I may have one account but if Group Admin approved my request I will became that group member and may see my predictions in that group. matches are the same but predictions will be group specific, with this we can provide this application to other groups to use. 


What we should do before moving this logic :
### Group Administrator concept.
Super Admin (which is only one user) may create group and create Group. then invite user by Email , this user will registered in that group and will became Group Admin 
if user is already registerd in system System admin will choose Group admin from existing users (all users must be available for super admin)

### Super Admin
super admin is only one person , with username/password and password can be reset from the account settings page
super admin should be automatically persist in DB with (in application.properties default userrname/passwrod should be instered, then password can be changed. )

Super admin can just sync data with football data api
create and manage groups.
invite group admins or select group admins from admi page
manually publish results.
Super admin can't participate to prediction . 


Other admin functionality is available (prediction related) is Group admin resposibility