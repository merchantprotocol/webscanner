Web scanner allows barcode scanning directly from any android web browser.

Web developers can utilize a simple javascript API to include scanning functionality within their website, complete with a camera preview.

The android applet provides a HTTP API that runs on localhost.
On request it captures from the camera and returns a json object with the following:

1. A base 64 preview image
2. Any decoded barcode

The javascript API is used for these things:

1. Setting a default callback for each barcode found.
2. Setting a fire-once callback (optional)
3. Checking that the HTTP API is online (That the android applet is open)
4. Shutting down the android applet when the user navigates away from the page
5. Redirecting to applet (if installed) or to the Google Play listing if applet isn't online.

## Example & Usage
https://wallaceit.com.au/androidwebscanner/example.html
(View source for usage)

## Supported Barcodes

| 1D product | 1D industrial | 2D
| ---------- | ------------- | --------------
| UPC-A      | Code 39       | QR Code
| UPC-E      | Code 93       | Data Matrix
| EAN-8      | Code 128      | Aztec (beta)
| EAN-13     | Codabar       | PDF 417 (beta)
|            | ITF           |
|            | RSS-14        |
|            | RSS-Expanded  |

This project uses the xzing library for barcode decoding, for more info about xzing see:

https://github.com/zxing/zxing/

