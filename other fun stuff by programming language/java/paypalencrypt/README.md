Quite an interesting tool, using the Eclipse SWT framework.

An overview of the functionality was that the company needed to provide products online translated into several languages and payable in several currencies.

The products were defined in Paypal, and it was necessary to encrypt buttons to protect against spoofed purchases. Paypal provided a specification for creating these buttons.

I set up an XML definition of Paypal products and on an adhoc basis this tool could be run to perform the following tasks:

1. Gather the latest currency exchange rates data from the Bank of Canada

2. Create a set of encrypted buttons by currency

3. Format an XML file for the webpage to use in XSLT (XML transformations) and update the website via FTP.

In this way the website would be automatically updated, a user could click on a dropdown to select language and another for currency and the page would update.


