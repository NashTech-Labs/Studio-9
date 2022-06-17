# Permissions

status: RFP

Permissions system provides access control for key entities (such as Flow and Table)
and lets to implement high level features supporting:  

- collaboration (in teams)
- sharing (of entities)
- ...

Permissions system could be organized based on experience of popular SaaS services,
such as:

- [Github](https://github.com)
- [Postman](https://www.getpostman.com/)

The both service have `Personal` and `Enterprise` (organization) levels of permissions 
(see how [Github implements organizations](https://github.com/blog/674-introducing-organizations)).
And let to keep data `Private` or `Public` (Github only).



### Collaboration
It's important to distinguish sharing and group access.
Collaboation is based on Enterprise (organization) level of access to the data.
When user becomes part of the organization (team), he could be granted an access to
organization's data (to read, write, etc).



### Sharing
(Experimental)

The feature lets user to provide access to preview and copy data to any other user
(or even not user at all). Sharing generates token to access specific data for specific user and sends
tokenized link by email and built-in notification system (for registered users only).

Recieving sharing notification, user can preview shared data and clone it to his private "library"
(private data is supposed to remain private even after copying).



### Library
Library lets user easily access and manage all the private (personal or enterprise) or public data.
There are three basic scopes (acting as "virtual folders"):

- Private
- Enterprise
- Public

Advanced features for data grouping could be also implelemented. For example, flags (such as "favorite") or tags (such as "important", "priority").
That features can help to organize data, but they have nothing with permissions system itself.

