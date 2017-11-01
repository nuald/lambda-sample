$(function () {
  var colorPool = {
    "hsl(210, 100%, 50%)" : null,
    "hsl(240, 100%, 50%)" : null,
    "hsl(270, 100%, 50%)" : null,
    "hsl(300, 100%, 50%)" : null,
    "hsl(330, 100%, 50%)" : null,
    "hsl(360, 100%, 50%)" : null,
    "hsl(30,  100%, 50%)" : null,
    "hsl(120, 100%, 20%)" : null
  };
  var colorMap = {};

  function getColor(key) {
    if (!colorMap[key]) {
      for (var colorKey in colorPool) {
        if (!colorPool[colorKey]) {
          colorPool[colorKey] = key;
          colorMap[key] = colorKey;
          break;
        }
      }
    }
    return colorMap[key];
  }

  function render() {
    $.getJSON("mqtt", function(data) {
      var vis = d3.select("#visualisation"),
        WIDTH = 1000,
        HEIGHT = 500,
        MARGINS = {
          top: 50,
          right: 20,
          bottom: 50,
          left: 50
        },
        xScale = d3.scaleLinear().range([MARGINS.left, WIDTH - MARGINS.right])
          .domain([d3.min(data, function(d) {
            return d.ts;
          }), d3.max(data, function(d) {
            return d.ts;
          })]),
        yScale = d3.scaleLinear().range([HEIGHT - MARGINS.top, MARGINS.bottom])
          .domain([d3.min(data, function(d) {
            return d.value;
          }), d3.max(data, function(d) {
            return d.value;
          })]),
        xAxis = d3.axisBottom(xScale),
        yAxis = d3.axisLeft(yScale);

      vis.append("svg:g")
        .attr("class", "x axis")
        .attr("transform", "translate(0," + (HEIGHT - MARGINS.bottom) + ")")
        .call(xAxis);
      vis.append("svg:g")
        .attr("class", "y axis")
        .attr("transform", "translate(" + (MARGINS.left) + ",0)")
        .call(yAxis);

      var lineGen = d3.line()
        .x(function(d) {
          return xScale(d.ts);
        })
        .y(function(d) {
          return yScale(d.value);
        })
        .curve(d3.curveLinear);

      var dataGroup = d3.nest()
        .key(function(d) {
          return d.sensor;
        })
        .entries(data);

      var lSpace = WIDTH / dataGroup.length;
      var legendRectSize = 18;
      var legendSpacing = 4;

      dataGroup.forEach(function(d, i) {
        var color = getColor(d.key);
        vis.append('svg:path')
          .attr('d', lineGen(d.values))
          .attr('stroke', color)
          .attr('stroke-width', 2)
          .attr('fill', 'none');

        var legend = vis.append('g')
          .attr('class', 'legend')
          .attr('transform', function() {
            var height = legendRectSize + legendSpacing;
            var offset =  height * dataGroup.length / 2;
            // var horz = -2 * legendRectSize;
            // var vert = i * height - offset;
            var horz = (lSpace / 2) + i * lSpace;
            var vert = HEIGHT - legendRectSize;
            return 'translate(' + horz + ',' + vert + ')';
          });
        legend.append('rect')
          .attr('width', legendRectSize)
          .attr('height', legendRectSize)
          .style('fill', color)
          .style('stroke', color);
        legend.append('text')
          .attr('x', legendRectSize + legendSpacing)
          .attr('y', legendRectSize - legendSpacing)
          .text(d.key);
        // vis.append("text")
        //   .attr("x", (lSpace / 2) + i * lSpace)
        //   .attr("y", HEIGHT)
        //   .style("fill", color)
        //   .attr("class", "legend")
        //   .text(d.key);
      });
    });
  }

  // setInterval(render, 1000);
  render();
});
