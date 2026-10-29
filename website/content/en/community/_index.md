---
title: Community
menu:
  main:
    weight: 40
---

<!--add blocks of content here to add more sections to the community page -->

<h2 class="text-center mb-5">The people behind alf.io</h2>
<div class="row">
  <div id="contributors" class="col-8 offset-2 row">

  </div>
</div>

<template id="contributor-template">
    <div class="col-xs-6 col-md-4">
        <div class="d-flex mb-4">
            <a href="#" class="contributor-link flex-shrink-0">
                <img class="img-thumbnail contributor-avatar" style="max-height: 120px" src="" alt="">
            </a>
            <div class="flex-grow-1 ms-2">
                <h4><span class="contributor-name"></span></h4>
                <p><i class="fas fa-code-branch"></i> contributions: <span class="contributor-contributions"></span></p>
                <p><i class="fab fa-github"></i> <a class="contributor-link"><span class="contributor-nickname"></span></a></p>
            </div>
        </div>
    </div>
</template>

<script>
    (function() {
        var template = document.getElementById('contributor-template');
        var container = document.getElementById('contributors');
        fetch('https://api.github.com/repos/alfio-event/alf.io/contributors')
            .then(function(response) { return response.json(); })
            .then(function(list) {
                list.forEach(function(contributor) {
                    var element = template.content.firstElementChild.cloneNode(true);
                    element.querySelectorAll('.contributor-link').forEach(function(link) {
                        link.href = contributor.html_url;
                    });
                    var avatar = element.querySelector('.contributor-avatar');
                    avatar.src = contributor.avatar_url;
                    element.querySelector('.contributor-nickname').textContent = contributor.login;
                    element.querySelector('.contributor-name').textContent = contributor.login;
                    element.querySelector('.contributor-contributions').textContent = contributor.contributions;
                    container.appendChild(element);
                    fetch(contributor.url)
                        .then(function(response) { return response.json(); })
                        .then(function(details) {
                            var name = contributor.login;
                            if (details.name && details.name.length > 0) {
                                name = details.name;
                            }
                            element.querySelector('.contributor-name').textContent = name;
                            avatar.alt = name;
                        });
                });
            });
    })();
</script>