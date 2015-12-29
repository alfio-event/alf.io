# nz-toggle

__Double and Triple-State Toggle for Angular__

## Features
- Native checkboxes are ugly, inconsistent, and involve insane CSS tweaks to style. nz-toggle is a simple directive that relieves all of the pain of styling `<input type="checkbox">`
- Indeterminate checkboxes are great for nested checklists, but emit a `false` value in that state. nz-toggle's `tri-toggle` mode fixes this by truthfully providing 3 separate value states.
- Tooltips for the tri-toggle impaired.

[Demo](http://codepen.io/anon/pen/yNjyME)

## Get Started

Install via NPM or Bower

`npm install --save nz-toggle`
`bower install --save nz-toggle`

Include Files

```html
<link rel="stylesheet" type="text/css" href=".../nz-toggle/dist/nz-toggle.min.css" />
<script type="text/javascript" src=".../nz-toggle/dist/nz-toggle.min.js"></script>
```

## Using the directive 

```html
<nz-toggle 
  tri-toggle 
  on-toggle="myFunction()" 
  ng-model="value">
</nz-toggle> 

<!-- Default Values : false-val = 0, null-val = null, true-val = true; -->
```

```html
<nz-toggle 
  tri-toggle 
  on-toggle="myFunction()" 
  ng-model="value" 
  val-true="'myString'" 
  val-false="0" 
  val-null="-1">
</nz-toggle> 

```

Visit the [demo](http://codepen.io/anon/pen/yNjyME) for more usage information

## License

The MIT License (MIT)

Copyright (c) 2014 Tanner Linsley

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
