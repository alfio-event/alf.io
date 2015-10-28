var gulp = require('gulp');
var ngAnnotate = require('gulp-ng-annotate');
var uglify = require('gulp-uglify');
var stylus = require('gulp-stylus');
var nib = require('nib');
var rename = require('gulp-rename');

var appFolder = './app/';
var prodFolder = './prod/';

gulp.task('watch', watchTask);

// Build Tasks //
gulp.task('js', jsTask);
gulp.task('less', lessTask);
gulp.task('build', ['js', 'less']);

// Default //
gulp.task('default', ['build', 'watch']);





function serveTask() {
    server.start({
        port: 80,
        directory: argv.prod ? './prod' : './app'
    });
}

function jsTask() {
    gulp.src('./src/nz-toggle.js')
        .pipe(ngAnnotate())
        .pipe(gulp.dest('./dist/'))
        .pipe(uglify())
        .pipe(rename('nz-toggle.min.js'))
        .pipe(gulp.dest('./dist/'));
}

function lessTask() {
    gulp.src('./src/nz-toggle.styl')
        .pipe(stylus({
            use: nib()
        }))
        .pipe(gulp.dest('./dist/'));

    return gulp.src('./src/nz-toggle.styl')
        .pipe(stylus({
            use: nib(),
            compress: true,
        }))
        .pipe(rename('nz-toggle.min.css'))
        .pipe(gulp.dest('./dist/'));
}

function watchTask() {
    return gulp.watch('./src/**', ['build']);
}
